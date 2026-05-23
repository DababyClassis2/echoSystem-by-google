package com.echosystem.localshare.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.echosystem.localshare.database.DeviceEntity
import com.echosystem.localshare.model.DeviceCandidate
import com.echosystem.localshare.model.NsdState
import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.repository.DeviceRepository
import com.echosystem.localshare.core.connection.ConnectionManager
import com.echosystem.localshare.server.ServerEventBus
import com.echosystem.localshare.service.EchoCoreService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val connectionManager: ConnectionManager,
    private val serverEventBus: ServerEventBus
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val serviceType = "_localshare._tcp."
    private val serviceName = "EchoShare_${android.os.Build.MODEL.replace(" ", "_")}"
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private val isRegistered = AtomicBoolean(false)

    private val _state = MutableStateFlow(NsdState.IDLE)
    val state = _state.asStateFlow()

    private var retryCount = 0
    private val MAX_RETRIES = 5

    private val serviceNameToDeviceId = ConcurrentHashMap<String, String>()
    private val offlineTimers = ConcurrentHashMap<String, Job>()

    @Synchronized
    fun registerService(port: Int) {
        if (_state.value == NsdState.REGISTERED) return
        
        scope.launch {
            _state.value = NsdState.IDLE
            acquireMulticastLockSafely()
            
            retryCount = 0
            while (retryCount < MAX_RETRIES) {
                if (isRegistered.get()) break
                
                if (performRegistration(port)) {
                    EchoCoreService.getInstance()?.updatePulse("nsd")
                    break
                }
                
                retryCount++
                val delayTime = 2000L * retryCount
                Log.w("NsdHelper", "Reg attempt $retryCount failed. Retrying in ${delayTime}ms...")
                delay(delayTime)
            }
            
            if (!isRegistered.get()) {
                Log.e("NsdHelper", "NSD Registration failed after $MAX_RETRIES attempts. Entering OFFLINE mode.")
                _state.value = NsdState.OFFLINE
            }
        }
    }

    private fun performRegistration(port: Int): Boolean {
        unregisterServiceGracefully()

        val info = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.setPort(port)
            
            val devId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_device"
            setAttribute("deviceId", devId)
            setAttribute("name", android.os.Build.MODEL)
            setAttribute("fingerprint", "FINGERPRINT-$devId-${android.os.Build.MODEL}-SECURE-SALT")
            setAttribute("version", "1.1.1")
            setAttribute("capabilities", "browse,download,upload,delete")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                Log.i("NsdHelper", "NSD Success: ${s.serviceName}")
                isRegistered.set(true)
                _state.value = NsdState.REGISTERED
                EchoCoreService.getInstance()?.updatePulse("nsd")
            }

            override fun onRegistrationFailed(s: NsdServiceInfo, err: Int) {
                Log.e("NsdHelper", "NSD Reg Failed: $err")
                isRegistered.set(false)
                _state.value = NsdState.IDLE
            }

            override fun onServiceUnregistered(s: NsdServiceInfo) {
                isRegistered.set(false)
                _state.value = NsdState.IDLE
            }

            override fun onUnregistrationFailed(s: NsdServiceInfo, err: Int) {
                Log.e("NsdHelper", "NSD Unreg Failed: $err")
            }
        }

        registrationListener = listener

        return try {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: Exception) {
            Log.e("NsdHelper", "Reg Exception: ${e.message}")
            false
        }
    }

    @Synchronized
    fun unregisterServiceGracefully() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) { /* Silent ignore */ }
        }
        registrationListener = null
        isRegistered.set(false)
        _state.value = NsdState.IDLE
    }

    fun discoverDevices(): Flow<DeviceCandidate> = callbackFlow {
        _state.value = if (_state.value == NsdState.REGISTERED) NsdState.REGISTERED else NsdState.DISCOVERING
        acquireMulticastLockSafely()
        
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Discovery started: $regType")
                EchoCoreService.getInstance()?.updatePulse("nsd")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                EchoCoreService.getInstance()?.updatePulse("nsd")
                if (service.serviceName != serviceName) {
                    resolveService(service) { candidate ->
                        trySend(candidate)
                    }
                }
            }

            override fun onServiceLost(s: NsdServiceInfo) {
                val devId = serviceNameToDeviceId[s.serviceName] ?: s.serviceName
                offlineTimers[devId]?.cancel()
                offlineTimers[devId] = scope.launch(Dispatchers.IO) {
                    delay(30000)
                    connectionManager.setDeviceOnline(devId, false)
                    serverEventBus.emit(ServerEvent.DeviceOffline(devId))
                    offlineTimers.remove(devId)
                }
            }

            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(st: String, err: Int) {
                Log.e("NsdHelper", "Discovery Start Fail: $err")
                _state.value = NsdState.OFFLINE
            }
            override fun onStopDiscoveryFailed(st: String, err: Int) {}
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Discover Exception: ${e.message}")
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) { }
            releaseMulticastLockSafely()
        }
    }

    private fun resolveService(service: NsdServiceInfo, onResolved: (DeviceCandidate) -> Unit) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(s: NsdServiceInfo, err: Int) {
                Log.w("NsdHelper", "Resolve failed: $err")
            }

            override fun onServiceResolved(s: NsdServiceInfo) {
                val ip = s.host.hostAddress
                if (ip != null) {
                    val id = s.attributes["deviceId"]?.let { String(it) } ?: s.serviceName
                    val name = s.attributes["name"]?.let { String(it) } ?: s.serviceName
                    val fp = s.attributes["fingerprint"]?.let { String(it) } ?: "FINGERPRINT-$id-$name"

                    serviceNameToDeviceId[s.serviceName] = id
                    
                    // Cancel any pending offline timer
                    offlineTimers[id]?.cancel()
                    offlineTimers.remove(id)

                    scope.launch(Dispatchers.IO) {
                        val existing = deviceRepository.getDeviceById(id)
                        val defaultStatus = existing?.trustStatus ?: "UNKNOWN"
                        val updated = DeviceEntity(
                            deviceId = id,
                            name = name,
                            ipAddress = ip,
                            trustStatus = defaultStatus,
                            lastSeen = System.currentTimeMillis(),
                            notes = existing?.notes ?: "",
                            permissions = existing?.permissions ?: "BROWSE_FILES,DOWNLOAD_FILES,UPLOAD_FILES,DELETE_FILES"
                        )
                        deviceRepository.insertDevice(updated)
                        connectionManager.setDeviceOnline(id, true)
                        serverEventBus.emit(ServerEvent.DeviceOnline(id, ip))
                    }

                    onResolved(DeviceCandidate(name, ip, s.port))
                }
            }
        }
        
        try {
            nsdManager.resolveService(service, resolver)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Resolve Exception: ${e.message}")
        }
    }

    private fun acquireMulticastLockSafely() {
        try {
            val hasPermission = context.checkCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                if (_state.value != NsdState.ERROR_DEGRADED) {
                    Log.w("NsdHelper", "CHANGE_WIFI_MULTICAST_STATE permission missing. Discovery may be slow on some routers.")
                    _state.value = NsdState.ERROR_DEGRADED
                }
                return
            }

            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("EchoShareNSD").apply {
                    setReferenceCounted(true)
                    try {
                        acquire()
                        Log.d("NsdHelper", "Multicast Lock Acquired")
                    } catch (e: SecurityException) {
                        Log.w("NsdHelper", "SecurityException during lock acquisition: ${e.message}")
                        _state.value = NsdState.ERROR_DEGRADED
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NsdHelper", "Multicast Lock setup failed: ${e.message}")
            _state.value = NsdState.ERROR_DEGRADED
        }
    }

    private fun releaseMulticastLockSafely() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("NsdHelper", "Multicast Lock Released")
                }
            }
        } catch (e: Exception) {
            Log.e("NsdHelper", "Error releasing lock: ${e.message}")
        } finally {
            multicastLock = null
        }
    }
}
