package com.echosystem.localshare.core.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import com.echosystem.localshare.core.NetworkMode
import com.echosystem.localshare.logging.AppLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthStatus { HEALTHY, DEGRADED, NONE }
enum class Restriction { NO_HOTSPOT_SUPPORT, CELLULAR_ONLY, LOCATION_REQUIRED }

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val mode: NetworkMode) : ConnectionState()
    data class Connected(
        val mode: NetworkMode,
        val ip: String,
        val health: HealthStatus,
        val restrictions: Set<Restriction>
    ) : ConnectionState()
    data class Failed(val error: String) : ConnectionState()
}

@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "ConnectionManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var healthCheckJob: Job? = null
    private var currentMode: NetworkMode = NetworkMode.LAN

    init {
        observeNetworkChanges()
        startHealthCheckLoop()
    }

    private fun observeNetworkChanges() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AppLogger.logEvent(tag, "Network Interface Available: $network")
                refreshState()
            }

            override fun onLost(network: Network) {
                AppLogger.logEvent(tag, "Network Interface Lost: $network")
                refreshState()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                refreshState()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                refreshState()
            }
        })
    }

    fun setNetworkMode(mode: NetworkMode) {
        currentMode = mode
        _connectionState.value = ConnectionState.Connecting(mode)
        refreshState()
    }

    private fun refreshState() {
        scope.launch {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

            if (activeNetwork == null || capabilities == null) {
                _connectionState.value = ConnectionState.Disconnected
                return@launch
            }

            val ip = linkProperties?.linkAddresses?.find { it.address is Inet4Address }?.address?.hostAddress ?: "0.0.0.0"
            
            val restrictions = mutableSetOf<Restriction>()
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                restrictions.add(Restriction.CELLULAR_ONLY)
            }

            // Real-time hotspot SSID detection (Simplified)
            var hotspotSsid: String? = null
            if (currentMode == NetworkMode.HOTSPOT) {
                // In actual deployment, we'd query the WifiManager reservation SSID
            }

            _connectionState.value = ConnectionState.Connected(
                mode = currentMode,
                ip = ip,
                health = HealthStatus.HEALTHY,
                restrictions = restrictions
            )
            
            AppLogger.logEvent(tag, "Refined Connection State: $currentMode | IP: $ip | Unrestricted: ${restrictions.isEmpty()}")
        }
    }

    private fun startHealthCheckLoop() {
        healthCheckJob?.cancel()
        healthCheckJob = scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(30000)
                val status = _connectionState.value
                if (status is ConnectionState.Connected) {
                    val isHealthy = performHealthCheck(status.ip)
                    if (!isHealthy) {
                        consecutiveFailures++
                        if (consecutiveFailures >= 2) {
                            _connectionState.value = status.copy(health = HealthStatus.DEGRADED)
                            AppLogger.logEvent(tag, "Network Mesh node at ${status.ip} is reporting DEGRADED health.")
                        }
                    } else {
                        consecutiveFailures = 0
                        if (status.health != HealthStatus.HEALTHY) {
                            _connectionState.value = status.copy(health = HealthStatus.HEALTHY)
                            AppLogger.logEvent(tag, "Network Mesh node health restored to HEALTHY.")
                        }
                    }
                }
            }
        }
    }

    private suspend fun performHealthCheck(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(ip).isReachable(2000)
        } catch (e: Exception) {
            false
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ConnectionModule {
    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): ConnectionManager {
        return ConnectionManager(context, scope)
    }
}
