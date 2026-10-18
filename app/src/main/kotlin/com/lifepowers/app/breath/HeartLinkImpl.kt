package com.lifepowers.app.breath

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifepowers.app.domain.HeartDevice
import com.lifepowers.app.domain.HeartLink
import com.lifepowers.app.domain.HeartLinkState
import com.lifepowers.core.Biofeedback
import com.lifepowers.core.HeartReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A live heart rate link over the standard Bluetooth LE heart rate service. Scans with a filter on the
 * service for 20 seconds, connects over LE, turns on measurement notifications, and appends each parsed
 * reading. A dropped link moves to LOST and is retried three times with a growing wait. Missing
 * permissions and Bluetooth being off are reported as states, never thrown. The last connected sensor
 * is remembered in the settings store.
 *
 * Bluetooth callbacks arrive on binder threads, so the link fields are guarded by [lock].
 */
@SuppressLint("MissingPermission") // Every call is behind hasPermissions() and a SecurityException catch.
@Singleton
class HeartLinkImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
) : HeartLink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val _state = MutableStateFlow(HeartLinkState.OFF)
    override val state: StateFlow<HeartLinkState> = _state.asStateFlow()

    private val _connected = MutableStateFlow<HeartDevice?>(null)
    override val connected: StateFlow<HeartDevice?> = _connected.asStateFlow()

    private val _readings = MutableStateFlow<List<HeartReading>>(emptyList())
    override val readings: StateFlow<List<HeartReading>> = _readings.asStateFlow()

    private val _nearby = MutableStateFlow<List<HeartDevice>>(emptyList())
    override val nearby: StateFlow<List<HeartDevice>> = _nearby.asStateFlow()

    override val remembered: Flow<HeartDevice?> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            prefs[KEY_ADDRESS]?.let { HeartDevice(it, prefs[KEY_NAME] ?: BreathMath.FALLBACK_SENSOR_NAME, 0) }
        }
        .distinctUntilChanged()

    // Guarded by lock.
    private var gatt: BluetoothGatt? = null
    private var target: HeartDevice? = null
    private var attempts = 0
    private var retryJob: Job? = null
    private var scanStopJob: Job? = null
    private var scanning = false

    override fun hasPermissions(): Boolean =
        BreathMath.requiredPermissions(Build.VERSION.SDK_INT).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    override fun startScan() {
        val adapter = readyAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _state.value = HeartLinkState.BLUETOOTH_OFF
            return
        }
        synchronized(lock) {
            if (scanning) {
                try {
                    scanner.stopScan(scanCallback)
                } catch (_: SecurityException) {
                } catch (_: IllegalStateException) {
                }
                scanning = false
            }
            _nearby.value = emptyList()
            val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build())
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            try {
                scanner.startScan(filters, settings, scanCallback)
                scanning = true
            } catch (_: SecurityException) {
                _state.value = HeartLinkState.NEEDS_PERMISSION
                return
            } catch (_: IllegalStateException) {
                // Thrown when Bluetooth turned off between the check and the call.
                _state.value = HeartLinkState.BLUETOOTH_OFF
                return
            }
            if (_state.value !in BUSY) _state.value = HeartLinkState.SCANNING
            scanStopJob?.cancel()
            scanStopJob = scope.launch {
                delay(SCAN_MS)
                stopScan()
            }
        }
    }

    override fun stopScan() {
        synchronized(lock) {
            scanStopJob?.cancel()
            scanStopJob = null
            if (!scanning) return
            scanning = false
            try {
                adapter()?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
            }
            if (_state.value == HeartLinkState.SCANNING) _state.value = HeartLinkState.OFF
        }
    }

    override fun connect(device: HeartDevice) {
        val adapter = readyAdapter() ?: return
        stopScan()
        val remote = remoteDevice(adapter, device.address) ?: return
        synchronized(lock) {
            retryJob?.cancel()
            retryJob = null
            closeGattLocked()
            target = device
            attempts = 0
            _readings.value = emptyList()
            _connected.value = null
            openLocked(remote)
        }
    }

    override fun connectRemembered() {
        scope.launch {
            val device = remembered.first() ?: return@launch
            val now = _state.value
            if (now == HeartLinkState.CONNECTING || now == HeartLinkState.CONNECTED) return@launch
            connect(device)
        }
    }

    override fun disconnect() {
        synchronized(lock) {
            retryJob?.cancel()
            retryJob = null
            target = null
            closeGattLocked()
            _connected.value = null
            _state.value = HeartLinkState.OFF
        }
    }

    private fun adapter(): BluetoothAdapter? =
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (_: SecurityException) {
            null
        }

    /** The adapter when permissions are granted and Bluetooth is on; otherwise reports the state and returns null. */
    private fun readyAdapter(): BluetoothAdapter? {
        if (!hasPermissions()) {
            _state.value = HeartLinkState.NEEDS_PERMISSION
            return null
        }
        val adapter = adapter()
        val on = try {
            adapter?.isEnabled == true
        } catch (_: SecurityException) {
            false
        }
        if (adapter == null || !on) {
            _state.value = HeartLinkState.BLUETOOTH_OFF
            return null
        }
        return adapter
    }

    private fun remoteDevice(adapter: BluetoothAdapter, address: String): BluetoothDevice? =
        try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            _state.value = HeartLinkState.NEEDS_PERMISSION
            null
        }

    private fun openLocked(remote: BluetoothDevice) {
        _state.value = HeartLinkState.CONNECTING
        gatt = try {
            remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            _state.value = HeartLinkState.NEEDS_PERMISSION
            target = null
            return
        }
        if (gatt == null) lostLocked(null)
    }

    private fun closeGattLocked() {
        val g = gatt ?: return
        gatt = null
        try {
            g.disconnect()
        } catch (_: SecurityException) {
        }
        safeClose(g)
    }

    private fun safeClose(g: BluetoothGatt) {
        try {
            g.close()
        } catch (_: SecurityException) {
        }
    }

    /** The link dropped or failed. Retries the same sensor up to [MAX_RETRIES] times, then stays LOST. */
    private fun lostLocked(g: BluetoothGatt?) {
        g?.let { safeClose(it) }
        gatt = null
        _connected.value = null
        val device = target
        if (device == null) {
            _state.value = HeartLinkState.OFF
            return
        }
        _state.value = HeartLinkState.LOST
        if (attempts >= MAX_RETRIES) return
        attempts++
        val wait = BreathMath.backoffMs(attempts)
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            val adapter = readyAdapter() ?: return@launch
            val remote = remoteDevice(adapter, device.address) ?: return@launch
            synchronized(lock) {
                if (target == device && gatt == null) openLocked(remote)
            }
        }
    }

    private fun markConnected() {
        val device = synchronized(lock) {
            attempts = 0
            target
        } ?: return
        _connected.value = device
        _state.value = HeartLinkState.CONNECTED
        scope.launch {
            runCatching {
                store.edit {
                    it[KEY_ADDRESS] = device.address
                    it[KEY_NAME] = device.name
                }
            }
        }
    }

    private fun isCurrent(g: BluetoothGatt): Boolean = synchronized(lock) { g === gatt }

    private fun enableNotifications(g: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            writeDescriptorLegacy(g, descriptor)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptorLegacy(g: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean {
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        return g.writeDescriptor(descriptor)
    }

    private fun onValue(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray?) {
        if (value == null || characteristic.uuid != HR_MEASUREMENT || !isCurrent(g)) return
        val reading = Biofeedback.parseHeartRateMeasurement(value, System.currentTimeMillis()) ?: return
        _readings.update { BreathMath.appendCapped(it, reading) }
        if (_state.value != HeartLinkState.CONNECTED) markConnected()
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = seen(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { seen(it) }

        override fun onScanFailed(errorCode: Int) {
            synchronized(lock) { scanning = false }
            if (_state.value == HeartLinkState.SCANNING) _state.value = HeartLinkState.OFF
        }
    }

    private fun seen(result: ScanResult) {
        val device = result.device ?: return
        val systemName = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        val found = HeartDevice(device.address, BreathMath.displayName(result.scanRecord?.deviceName, systemName), result.rssi)
        _nearby.update { BreathMath.mergeNearby(it, found) }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            synchronized(lock) {
                if (g !== gatt) {
                    safeClose(g)
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    val started = try {
                        g.discoverServices()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!started) lostLocked(g)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    lostLocked(g)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (!isCurrent(g)) return
            val characteristic = if (status == BluetoothGatt.GATT_SUCCESS) {
                g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            } else {
                null
            }
            if (characteristic == null) {
                // Not a heart rate sensor after all; retrying would not help.
                synchronized(lock) {
                    target = null
                    closeGattLocked()
                    _connected.value = null
                    _state.value = HeartLinkState.OFF
                }
                return
            }
            try {
                g.setCharacteristicNotification(characteristic, true)
                val ccc = characteristic.getDescriptor(CLIENT_CONFIG)
                when {
                    ccc == null -> markConnected()
                    !enableNotifications(g, ccc) -> synchronized(lock) { if (g === gatt) lostLocked(g) }
                }
            } catch (_: SecurityException) {
                _state.value = HeartLinkState.NEEDS_PERMISSION
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != CLIENT_CONFIG || !isCurrent(g)) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                markConnected()
            } else {
                synchronized(lock) { if (g === gatt) lostLocked(g) }
            }
        }

        /** Android 13 and later. */
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) =
            onValue(g, characteristic, value)

        /** Before Android 13. On 13 and later the overload above replaces it, so a reading is never counted twice. */
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) =
            onValue(g, characteristic, characteristic.value)
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        const val SCAN_MS = 20_000L
        const val MAX_RETRIES = 3

        private val BUSY = setOf(HeartLinkState.CONNECTING, HeartLinkState.CONNECTED, HeartLinkState.LOST)
        private val KEY_ADDRESS = stringPreferencesKey("breath_heart_address")
        private val KEY_NAME = stringPreferencesKey("breath_heart_name")
    }
}
