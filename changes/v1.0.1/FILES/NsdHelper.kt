package com.echosystem.localshare.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.echosystem.localshare.model.DeviceCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_echoshare._tcp."
    private val serviceName = "EchoShare_${android.os.Build.MODEL.replace(" ", "_")}"
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeRegistrationListener: NsdManager.RegistrationListener? = null
    private val isRegistered = AtomicBoolean(false)

    @Synchronized
    fun registerService(port: Int) {
        scope.launch {
            var attempt = 1
            val maxAttempts = 10
            var delayMs = 2000L

            while (attempt <= maxAttempts) {
                if (isRegistered.get()) {
                    Log.d("NsdHelper", "Already registered, skipping...")
                    break
                }

                val success = performRegister(port)
                if (success) {
                    Log.d("NsdHelper", "NSD Success triggered on attempt $attempt")
                    break
                } else {
                    Log.e("NsdHelper", "NSD Failed on attempt $attempt, retrying in ${delayMs}ms...")
                    delay(delayMs)
                    delayMs = (delayMs * 1.5).toLong().coerceAtMost(30000L)
                    attempt++
                }
            }
        }
    }

    private fun performRegister(port: Int): Boolean {
        unregisterServiceGracefully()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(regServiceInfo: NsdServiceInfo) {
                Log.i("NsdHelper", "NSD: Service registered: ${regServiceInfo.serviceName}")
                isRegistered.set(true)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "NSD: Registration failed: $errorCode")
                isRegistered.set(false)
                activeRegistrationListener = null
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i("NsdHelper", "NSD: Service unregistered")
                isRegistered.set(false)
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "NSD: Unregistration failed: $errorCode")
            }
        }

        activeRegistrationListener = listener

        return try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: Exception) {
            Log.e("NsdHelper", "NSD: Exception in register: ${e.message}")
            false
        }
    }

    @Synchronized
    fun unregisterServiceGracefully() {
        activeRegistrationListener?.let { 
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.w("NsdHelper", "NSD: Unregister exception (likely already dead): ${e.message}")
            }
        }
        activeRegistrationListener = null
        isRegistered.set(false)
    }

    fun discoverDevices(): Flow<DeviceCandidate> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "NSD: Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NsdHelper", "NSD: Found potential peer: ${service.serviceName}")
                
                // Be more permissive with types, check for prefix if needed
                if (service.serviceName != serviceName) {
                    resolveServiceSafely(service) { candidate ->
                        trySend(candidate)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("NsdHelper", "NSD: Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(regType: String) {
                Log.d("NsdHelper", "NSD: Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "NSD: Start discovery failed: $errorCode")
                // Re-attempt discovery if busy
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                    // Log but don't crash loop
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "NSD: Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "NSD: Discovery exception: ${e.message}")
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.w("NsdHelper", "NSD: Stop discovery exception: ${e.message}")
            }
        }
    }

    private fun resolveServiceSafely(service: NsdServiceInfo, onResolved: (DeviceCandidate) -> Unit) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("NsdHelper", "NSD: Resolve failed for ${serviceInfo.serviceName}: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i("NsdHelper", "NSD: Resolved ${serviceInfo.serviceName} at ${serviceInfo.host.hostAddress}")
                val host = serviceInfo.host.hostAddress
                if (host != null) {
                    onResolved(DeviceCandidate(serviceInfo.serviceName, host, serviceInfo.port))
                }
            }
        }

        try {
            nsdManager.resolveService(service, resolveListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "NSD: Resolve exception: ${e.message}")
        }
    }
}
