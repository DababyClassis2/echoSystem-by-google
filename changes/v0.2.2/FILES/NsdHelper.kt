package com.echosystem.localshare.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.echosystem.localshare.model.DeviceCandidate
import com.echosystem.localshare.service.EchoCoreService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
    private val serviceType = "_echoshare._tcp."
    private val serviceName = "EchoShare_${android.os.Build.MODEL.replace(" ", "_")}"
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private val isRegistered = AtomicBoolean(false)

    @Synchronized
    fun registerService(port: Int) {
        scope.launch {
            // Re-acquire lock for each registration attempt
            acquireMulticastLockSafely()
            
            var attempt = 1
            while (attempt <= 5) {
                if (isRegistered.get()) break
                
                if (performRegistration(port)) {
                    // Report pulse back to service for watchdog
                    EchoCoreService.getInstance()?.updatePulse("nsd")
                    break
                }
                
                Log.e("NsdHelper", "Reg attempt $attempt failed. Backing off...")
                delay(2000L * attempt)
                attempt++
            }
        }
    }

    private fun performRegistration(port: Int): Boolean {
        unregisterServiceGracefully()

        val info = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) {
                Log.i("NsdHelper", "NSD Success: ${s.serviceName}")
                isRegistered.set(true)
                EchoCoreService.getInstance()?.updatePulse("nsd")
            }

            override fun onRegistrationFailed(s: NsdServiceInfo, err: Int) {
                Log.e("NsdHelper", "NSD Reg Failed: $err")
                isRegistered.set(false)
            }

            override fun onServiceUnregistered(s: NsdServiceInfo) {
                isRegistered.set(false)
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
    }

    fun discoverDevices(): Flow<DeviceCandidate> = callbackFlow {
        acquireMulticastLockSafely()
        
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Discovery started: $regType")
                EchoCoreService.getInstance()?.updatePulse("nsd")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Ensure pulse continues on activity
                EchoCoreService.getInstance()?.updatePulse("nsd")
                
                if (service.serviceName != serviceName) {
                    resolveService(service) { candidate ->
                        trySend(candidate)
                    }
                }
            }

            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(st: String, err: Int) {
                Log.e("NsdHelper", "Discovery Start Fail: $err")
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
                    onResolved(DeviceCandidate(s.serviceName, ip, s.port))
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
            // Check for permission first
            val hasPermission = context.checkCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_MULTICAST_STATE) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.w("NSD_WATCHDOG", "Permission CHANGE_WIFI_MULTICAST_STATE missing. Discovery may be degraded.")
                return
            }

            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("EchoShareNSD").apply {
                    setReferenceCounted(true)
                    try {
                        acquire()
                        Log.d("NsdHelper", "Multicast Lock Acquired")
                    } catch (e: SecurityException) {
                        Log.w("NSD_WATCHDOG", "SecurityException during multicast lock acquire: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NSD_WATCHDOG", "Failed to setup multicast lock: ${e.message}")
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
            Log.e("NSD_WATCHDOG", "Error releasing multicast lock: ${e.message}")
        } finally {
            multicastLock = null
        }
    }

    // Deprecated legacy callers
    private fun acquireMulticastLock() = acquireMulticastLockSafely()
    private fun releaseMulticastLock() = releaseMulticastLockSafely()
}
