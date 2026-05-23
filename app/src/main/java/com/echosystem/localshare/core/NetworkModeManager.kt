package com.echosystem.localshare.core

import android.content.Context
import com.echosystem.localshare.core.connection.ConnectionManager
import com.echosystem.localshare.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: CoreEventBus,
    private val connectionManager: ConnectionManager,
    private val scope: CoroutineScope
) {
    private val tag = "NetworkModeManager"
    
    private val _currentMode = MutableStateFlow(NetworkMode.LAN)
    val currentMode: StateFlow<NetworkMode> = _currentMode.asStateFlow()

    init {
        scope.launch {
            connectionManager.connectionState.collect { state ->
                val oldMode = _currentMode.value
                val targetMode = state.currentMode
                if (oldMode != targetMode) {
                    _currentMode.value = targetMode
                    AppLogger.logEvent(tag, "Local Network Mode synchronized to: $targetMode")
                }
            }
        }
    }

    /**
     * Inspects active interfaces to decide on the most favorable direct mode.
     */
    fun selectBestMode(): NetworkMode {
        return connectionManager.selectBestMode()
    }

    /**
     * Safe mode transitioner; delegates to unified ConnectionManager.
     */
    fun switchMode(targetMode: NetworkMode) {
        connectionManager.setNetworkMode(targetMode)
    }

    /**
     * Failover routine when direct client-side pairing drops.
     */
    fun fallbackIfModeFails() {
        val failingMode = _currentMode.value
        val nextMode = when (failingMode) {
            NetworkMode.LAN -> NetworkMode.WIFI_DIRECT
            NetworkMode.WIFI_DIRECT -> NetworkMode.HOTSPOT
            NetworkMode.HOTSPOT -> NetworkMode.WEB_DASHBOARD
            NetworkMode.WEB_DASHBOARD -> NetworkMode.LAN // cycle gracefully
        }
        AppLogger.logEvent(tag, "Failover Trigger: $failingMode degraded. Repositioning to: $nextMode")
        switchMode(nextMode)
    }
}
