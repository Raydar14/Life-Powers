package com.lifepowers.app.world

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.lifepowers.core.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** One shot device location from the platform LocationManager. No Google Play services. */
@Singleton
class DeviceLocation @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** A fresh fix on API 30 and up (20 second limit), else the newest last known fix. Null without permission. */
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = freshProvider(lm)
            if (provider != null) currentFix(lm, provider)?.let { return it }
        }
        return lastKnown(lm)
    }

    /** "Portland, Oregon" when the platform geocoder is present and answers, else null. */
    @Suppress("DEPRECATION")
    suspend fun label(point: GeoPoint): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            val address = Geocoder(context, Locale.getDefault()).getFromLocation(point.lat, point.lon, 1)?.firstOrNull()
                ?: return@withContext null
            listOfNotNull(address.locality ?: address.subAdminArea, address.adminArea).distinct().joinToString(", ").ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "geocoder unavailable: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun enabled(lm: LocationManager, provider: String): Boolean =
        try { lm.isProviderEnabled(provider) } catch (_: Exception) { false }

    private fun freshProvider(lm: LocationManager): String? {
        val all = lm.allProviders
        return listOf(FUSED, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in all && enabled(lm, it) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingPermission")
    private suspend fun currentFix(lm: LocationManager, provider: String): Location? = try {
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context), Consumer<Location> { loc ->
                    if (cont.isActive) cont.resume(loc)
                })
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "current location failed on $provider: ${e.javaClass.simpleName}")
        null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? {
        val all = lm.allProviders
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER, FUSED)
            .filter { it in all }
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }
            .maxByOrNull { it.time }
    }

    private companion object {
        const val TAG = "World"
        /** LocationManager.FUSED_PROVIDER, a constant only from API 31; some devices list it earlier. */
        const val FUSED = "fused"
        const val FIX_TIMEOUT_MS = 20_000L
    }
}
