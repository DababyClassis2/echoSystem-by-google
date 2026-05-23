package com.echosystem.localshare.core

import com.echosystem.localshare.core.connection.ConnectionManager
import com.echosystem.localshare.core.connection.ConnectionState
import com.echosystem.localshare.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkModeManager @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val scope: CoroutineScope
) {
    private val tag = "NetworkModeManager"
    
    private val _currentMode = MutableStateFlow(NetworkMode.LAN)
    val currentMode: StateFlow<NetworkMode> = _currentMode.asStateFlow()

    init {
        // Observer pattern: Syncing legacy currentMode with the new Unified ConnectionManager
        scope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        _currentMode.value = state.mode
                        AppLogger.logEvent(tag, "Unified Engine synced: Current Mode is ${state.mode}")
                    }
                    is ConnectionState.Connecting -> {
                        _currentMode.value = state.mode
                    }
                    else -> {}
                }
            }
        }
    }

    fun switchMode(targetMode: NetworkMode) {
        AppLogger.logEvent(tag, "Mode change requested: $targetMode. Delegating to Unified ConnectionManager.")
        connectionManager.setNetworkMode(targetMode)
    }

    fun selectBestMode(): NetworkMode {
        // In the refinement, we trust the current state or choose LAN as safest default
        return _currentMode.value
    }

    fun fallbackIfModeFails() {
        // Failover routine: Cycle through available modes
        val nextMode = when (_currentMode.value) {
            NetworkMode.LAN -> NetworkMode.WIFI_DIRECT
            NetworkMode.WIFI_DIRECT -> NetworkMode.HOTSPOT
            NetworkMode.HOTSPOT -> NetworkMode.WEB_DASHBOARD
            NetworkMode.WEB_DASHBOARD -> NetworkMode.LAN
        }
        switchMode(nextMode)
    }
}
