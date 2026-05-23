package com.echosystem.localshare.core.connection

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.echosystem.localshare.core.CoreEvent
import com.echosystem.localshare.core.CoreEventBus
import com.echosystem.localshare.core.NetworkMode
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.web.NetworkUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ConnectionState encapsulates the reactive state of direct device-to-device connections.
 */
data class ConnectionState(
    val currentMode: NetworkMode = NetworkMode.LAN,
    val isConnected: Boolean = false,
    val deviceIp: String? = null,
    val hotspotSsid: String? = null,
    val error: String? = null
)

/**
 * ConnectionManager is the single source of truth class that integrates LAN detection,
 * local-only hotspots for Android 8-14, Wi-Fi Direct p2p group configuration, and dynamic IP resolution.
 */
@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventBus: CoreEventBus,
    private val scope: CoroutineScope
) {
    private val tag = "ConnectionManager"
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Reactive StateFlow exposed to modules and companion companion viewmodels
    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // LAN callback
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Hotspot reservation handles (Android 8+)
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null

    // Wi-Fi Direct peer-to-peer managers
    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var isGroupCreated = false

    init {
        // Enforce initial evaluation of the best network channel
        selectBestMode()
    }

    /**
     * Dynamically determines whether standard LAN Wi-Fi is present, falling back to P2P or Local Hotspots.
     */
    fun selectBestMode(): NetworkMode {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val chosenMode = if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkMode.LAN
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkMode.LAN
                else -> NetworkMode.WIFI_DIRECT
            }
        } else {
            NetworkMode.HOTSPOT
        }

        setNetworkMode(chosenMode)
        return chosenMode
    }

    /**
     * Transition active direct-link technology. Safely clears background processes of previous links.
     */
    @Synchronized
    fun setNetworkMode(targetMode: NetworkMode) {
        val oldMode = _connectionState.value.currentMode
        AppLogger.logEvent(tag, "Transfer network transitioning from $oldMode to $targetMode")

        // Teardown existing protocols
        stopActiveHotspot()
        stopActiveWifiDirect()
        stopLanMonitoring()

        when (targetMode) {
            NetworkMode.LAN -> startLanMonitoring()
            NetworkMode.WIFI_DIRECT -> startWifiDirectGroup()
            NetworkMode.HOTSPOT -> startHotspot()
            NetworkMode.WEB_DASHBOARD -> startWebDashboardMode()
        }

        // Convey network updates globally to Ktor servers and core managers
        if (oldMode != targetMode) {
            eventBus.tryEmit(CoreEvent.NetworkModeChanged(oldMode, targetMode))
        }
    }

    /**
     * LAN monitoring registers callbacks to dynamically capture updates in current IP of network interface
     */
    private fun startLanMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val ipAddress = NetworkUtils.getLocalIpAddress()
                AppLogger.logEvent(tag, "LAN connectivity obtained. Subnet Node IP: $ipAddress")
                _connectionState.update {
                    it.copy(
                        currentMode = NetworkMode.LAN,
                        isConnected = true,
                        deviceIp = ipAddress,
                        hotspotSsid = null,
                        error = null
                    )
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                AppLogger.logEvent(tag, "LAN gateway interface disconnected.")
                val ipAddress = NetworkUtils.getLocalIpAddress()
                _connectionState.update {
                    it.copy(
                        isConnected = ipAddress != "127.0.0.1",
                        deviceIp = if (ipAddress == "127.0.0.1") null else ipAddress
                    )
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                val currentIp = NetworkUtils.getLocalIpAddress()
                _connectionState.update { it.copy(deviceIp = currentIp) }
            }
        }

        networkCallback = callback
        try {
            connectivityManager.registerNetworkCallback(request, callback)
            val ipAddress = NetworkUtils.getLocalIpAddress()
            _connectionState.update {
                it.copy(
                    currentMode = NetworkMode.LAN,
                    isConnected = ipAddress != "127.0.0.1",
                    deviceIp = ipAddress,
                    hotspotSsid = null,
                    error = null
                )
            }
        } catch (e: Exception) {
            AppLogger.logEvent(tag, "Failed registering network observer: ${e.message}")
            updateError("LAN Registration Error: ${e.message}")
        }
    }

    private fun stopLanMonitoring() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) { /* Silent ignore */ }
        }
        networkCallback = null
    }

    /**
     * LocalOnlyHotspot handles Android 8-14 secure AP sharing.
     */
    @SuppressLint("MissingPermission")
    private fun startHotspot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            updateError("LocalOnlyHotspot is unsupported on old API levels (< Oreo 26)")
            return
        }

        if (!hasRequiredPermissions()) {
            updateError("Hotspot initialization dropped: Missing required Wi-Fi permissions.")
            return
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val handler = Handler(Looper.getMainLooper())

            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    super.onStarted(reservation)
                    hotspotReservation = reservation
                    
                    @Suppress("DEPRECATION")
                    val configuration = reservation.wifiConfiguration
                    val ssidName = configuration?.SSID ?: "echoSystem_Hotspot"
                    
                    // Default configuration IP for LocalOnlyHotspot on Pixel and general models
                    val softApIp = "192.168.43.1"
                    AppLogger.logEvent(tag, "Hotspot active. SSID: $ssidName, AP Gateway IP: $softApIp")
                    
                    _connectionState.update {
                        it.copy(
                            currentMode = NetworkMode.HOTSPOT,
                            isConnected = true,
                            deviceIp = softApIp,
                            hotspotSsid = ssidName,
                            error = null
                        )
                    }
                }

                override fun onStopped() {
                    super.onStopped()
                    AppLogger.logEvent(tag, "Hotspot reservation closed.")
                    hotspotReservation = null
                    _connectionState.update {
                        it.copy(
                            isConnected = false,
                            hotspotSsid = null
                        )
                    }
                }

                override fun onFailed(reason: Int) {
                    super.onFailed(reason)
                    val errorDetail = "Local hotspot connection error $reason"
                    AppLogger.logEvent(tag, errorDetail)
                    hotspotReservation = null
                    _connectionState.update {
                        it.copy(
                            isConnected = false,
                            error = errorDetail
                        )
                    }
                }
            }, handler)
        } catch (e: Exception) {
            AppLogger.logEvent(tag, "Exception initiating LocalHotspot process: ${e.message}")
            updateError("Hotspot Engine Exception: ${e.message}")
        }
    }

    private fun stopActiveHotspot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hotspotReservation?.let {
                try {
                    it.close()
                } catch (e: Exception) {
                    AppLogger.logEvent(tag, "Error closing hotspot handle: ${e.message}")
                }
            }
        }
        hotspotReservation = null
    }

    /**
     * Wi-Fi Direct (P2P Group Owner) configuration
     */
    @SuppressLint("MissingPermission")
    private fun startWifiDirectGroup() {
        if (!hasRequiredPermissions()) {
            updateError("Wi-Fi Direct Group build failed: Missing geolocation/near-wifi permissions.")
            return
        }

        try {
            p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
            p2pManager?.let { manager ->
                p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
                p2pChannel?.let { channel ->
                    manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            isGroupCreated = true
                            val p2pIp = "192.168.49.1" // Standard Group Owner IP
                            AppLogger.logEvent(tag, "Wi-Fi Direct cluster up. Owner node IP: $p2pIp")
                            
                            _connectionState.update {
                                it.copy(
                                    currentMode = NetworkMode.WIFI_DIRECT,
                                    isConnected = true,
                                    deviceIp = p2pIp,
                                    hotspotSsid = "P2P Cluster",
                                    error = null
                                )
                            }
                        }

                        override fun onFailure(reason: Int) {
                            val errorString = "Wi-Fi Direct connection instantiation failed with reason: $reason"
                            AppLogger.logEvent(tag, errorString)
                            _connectionState.update {
                                it.copy(
                                    isConnected = false,
                                    error = errorString
                                )
                            }
                        }
                    })
                } ?: run {
                    updateError("P2P communication channel initialization failed.")
                }
            } ?: run {
                updateError("Wi-Fi Direct feature set is missing on this controller.")
            }
        } catch (e: Exception) {
            AppLogger.logEvent(tag, "Exception executing p2p cluster setup: ${e.message}")
            updateError("Wi-Fi Direct Core Exception: ${e.message}")
        }
    }

    private fun stopActiveWifiDirect() {
        if (isGroupCreated && p2pManager != null && p2pChannel != null) {
            try {
                p2pManager?.removeGroup(p2pChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        AppLogger.logEvent(tag, "Successfully disbanded P2P mesh cluster.")
                    }

                    override fun onFailure(reason: Int) {
                        AppLogger.logEvent(tag, "Could not disassemble p2p cluster: $reason")
                    }
                })
            } catch (e: Exception) {
                AppLogger.logEvent(tag, "Exception during P2P group shutdown: ${e.message}")
            }
        }
        isGroupCreated = false
        p2pChannel = null
        p2pManager = null
    }

    private fun startWebDashboardMode() {
        val nodeIp = NetworkUtils.getLocalIpAddress()
        _connectionState.update {
            it.copy(
                currentMode = NetworkMode.WEB_DASHBOARD,
                isConnected = true,
                deviceIp = nodeIp,
                hotspotSsid = null,
                error = null
            )
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val list = mutableListOf(
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            list.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return list.all { context.checkCallingOrSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }

    private fun updateError(message: String) {
        AppLogger.logEvent(tag, "Connection Fault: $message")
        _connectionState.update {
            it.copy(error = message)
        }
    }

    fun stopAll() {
        stopActiveHotspot()
        stopActiveWifiDirect()
        stopLanMonitoring()
    }
}

/**
 * Hilt module offering connection management injection patterns
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectionModule {
    @Provides
    @Singleton
    fun provideConnectionManager(
        @ApplicationContext context: Context,
        eventBus: CoreEventBus,
        scope: CoroutineScope
    ): ConnectionManager {
        return ConnectionManager(context, eventBus, scope)
    }
}
