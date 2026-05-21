package com.echosystem.localshare.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.echosystem.localshare.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: CoreEventBus
) {
    private val tag = "NetworkModeManager"
    
    private val _currentMode = MutableStateFlow(NetworkMode.LAN)
    val currentMode: StateFlow<NetworkMode> = _currentMode.asStateFlow()

    init {
        // Initial auto-evaluation matching existing network parameters
        selectBestMode()
    }

    /**
     * Inspects active interfaces to decide on the most favorable direct mode.
     */
    fun selectBestMode(): NetworkMode {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val chosenMode = if (capabilities != null) {
            when {
                // If attached over standard Wi-Fi, Ethernet or local direct connections
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkMode.LAN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkMode.LAN
                // Fallback to offline configurations
                else -> NetworkMode.WIFI_DIRECT
            }
        } else {
            NetworkMode.HOTSPOT // offline hotspot defaults
        }

        switchMode(chosenMode)
        return chosenMode
    }

    /**
     * Safe mode transitioner; emits CoreEvents to app subscribers.
     */
    fun switchMode(targetMode: NetworkMode) {
        val oldMode = _currentMode.value
        if (oldMode != targetMode) {
            _currentMode.value = targetMode
            AppLogger.logEvent(tag, "Changing active network channel from $oldMode to $targetMode")
            
            // Dispatch event to allow server netty endpoints or companion VMs to adjust ports/insets
            eventBus.tryEmit(CoreEvent.NetworkModeChanged(oldMode, targetMode))
        }
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
