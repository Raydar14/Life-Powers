package com.lifepowers.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which connectors are being synced right now. Written by the worker, read by the status flow. */
@Singleton
class SyncRunState @Inject constructor() {
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running

    fun start(connectorId: String) {
        _running.value = _running.value + connectorId
    }

    fun finish(connectorId: String) {
        _running.value = _running.value - connectorId
    }
}
