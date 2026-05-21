package com.echosystem.localshare.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.echosystem.localshare.model.DeviceCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = this@NsdHelper.serviceName
            this.serviceType = this@NsdHelper.serviceType
            this.setPort(port)
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("NsdHelper", "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(NsdServiceInfo: NsdServiceInfo, errorCode: Int) {}
        })
    }

    fun discoverDevices(): Flow<DeviceCandidate> = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType && service.serviceName != serviceName) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NsdHelper", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host.hostAddress
                            if (host != null) {
                                trySend(DeviceCandidate(serviceInfo.serviceName, host, serviceInfo.port))
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose {
            nsdManager.stopServiceDiscovery(discoveryListener)
        }
    }
}
