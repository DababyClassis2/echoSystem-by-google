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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_echoshare._tcp."
    private val serviceName = "EchoShare_${android.os.Build.MODEL}"
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeRegistrationListener: NsdManager.RegistrationListener? = null
    private var isRegistered = false

    @Synchronized
    fun registerService(port: Int) {
        scope.launch {
            var attempt = 1
            var delayMs = 1000L
            val maxAttempts = 5

            while (attempt <= maxAttempts) {
                val registeredSuccess = performRegister(port)
                if (registeredSuccess) {
                    Log.d("NsdHelper", "NSD Service registered successfully on attempt $attempt")
                    break
                } else {
                    Log.e("NsdHelper", "NSD Service registration failed, retrying in ${delayMs}ms (attempt $attempt / $maxAttempts)...")
                    delay(delayMs)
                    delayMs *= 2 // Exponential backoff
                    attempt++
                }
            }
        }
    }

    @Synchronized
    private fun performRegister(port: Int): Boolean {
        // Safe reset/unregister first
        unregisterServiceGracefully()

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(regServiceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service registered successfully: ${regServiceInfo.serviceName}")
                isRegistered = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Registration failed with errorCode: $errorCode")
                isRegistered = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service unregistered successfully")
                isRegistered = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Unregistration failed: $errorCode")
                isRegistered = false
            }
        }

        activeRegistrationListener = listener

        return try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: Exception) {
            Log.e("NsdHelper", "Exception during registerService: ${e.message}")
            false
        }
    }

    @Synchronized
    fun unregisterServiceGracefully() {
        activeRegistrationListener?.let { currentListener ->
            try {
                nsdManager.unregisterService(currentListener)
                Log.d("NsdHelper", "Pre-existing registration listener unregistered successfully")
            } catch (e: Exception) {
                Log.e("NsdHelper", "Useless/already unregistered listener: ${e.message}")
            }
        }
        activeRegistrationListener = null
        isRegistered = false
    }

    fun discoverDevices(): Flow<DeviceCandidate> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "NSD discovery started gracefully.")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // Check matching type, ignore self
                if (service.serviceType == serviceType && service.serviceName != serviceName) {
                    try {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.e("NsdHelper", "Service resolve failed: $errorCode")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host.hostAddress
                                if (host != null) {
                                    trySend(DeviceCandidate(serviceInfo.serviceName, host, serviceInfo.port))
                                }
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("NsdHelper", "Exception resolving NSD service: ${e.message}")
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("NsdHelper", "NSD service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(regType: String) {
                Log.d("NsdHelper", "NSD discovery stopped.")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Exception starting service discovery: ${e.message}")
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                Log.e("NsdHelper", "Exception stopping service discovery in callbackFlow close: ${e.message}")
            }
        }
    }
}
