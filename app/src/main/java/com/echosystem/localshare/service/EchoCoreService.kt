package com.echosystem.localshare.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.server.ServerEventBus
import com.echosystem.localshare.server.routes.deviceRoutes
import com.echosystem.localshare.server.routes.fileRoutes
import com.echosystem.localshare.server.routes.pairingRoutes
import com.echosystem.localshare.server.routes.webSocketRoutes
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class EchoCoreService : Service() {

    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var pairingManager: PairingManager
    @Inject lateinit var trustManager: TrustManager
    @Inject lateinit var serverEventBus: ServerEventBus

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    
    // Dedicated scopes for absolute isolation
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastNettyPulse = 0L
    private var lastNsdPulse = 0L
    private var isShuttingDown = false

    companion object {
        private var activeInstance: EchoCoreService? = null
        fun getInstance(): EchoCoreService? = activeInstance
        private const val NOTIFICATION_ID = 42
    }

    override fun onCreate() {
        // [V1.0.3 CRITICAL] FIRST LINE ALWAYS
        startForegroundSafe()
        
        super.onCreate()
        activeInstance = this
        Log.i("EchoCoreService", "Service instantiated and entered foreground.")
        
        // Start sub-engines in their own protected bubbles
        igniteEngines()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // [V1.0.3 CRITICAL] RE-CONFIRM FOREGROUND ON RESTART
        startForegroundSafe()
        return START_STICKY
    }

    private fun startForegroundSafe() {
        val channelId = "echo_core_v103"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "EchoCore Stable Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required for high-performance local file sync"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LocalShare Core Active")
            .setContentText("Stable local portal engine is online.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("EchoCoreService", "FATAL: Could not entry foreground: ${e.message}")
        }
    }

    private fun igniteEngines() {
        engineScope.launch { restartNettyServer() }
        discoveryScope.launch { restartDiscoveryServer() }
    }

    private fun startWatchdog() {
        serviceScope.launch {
            while (!isShuttingDown) {
                delay(30000) // 30s check interval
                
                val now = System.currentTimeMillis()
                
                // Watchdog for Netty
                if (now - lastNettyPulse > 45000 && lastNettyPulse > 0) {
                    Log.w("Watchdog", "Netty Pulse lost. Reviving server...")
                    engineScope.launch { restartNettyServer() }
                }

                // Watchdog for NSD
                if (now - lastNsdPulse > 60000 && lastNsdPulse > 0) {
                    Log.w("Watchdog", "NSD Discovery stalled. Resetting discovery stack...")
                    discoveryScope.launch { restartDiscoveryServer() }
                }
            }
        }
    }

    private val restartMutex = Mutex()

    fun restartNetty() {
        engineScope.launch {
            restartNettyServer()
        }
    }

    fun restartNsd() {
        discoveryScope.launch {
            restartDiscoveryServer()
        }
    }

    private suspend fun restartNettyServer() {
        restartMutex.lock()
        try {
            server?.stop(500, 1000)
            server = null
            
            server = embeddedServer(Netty, port = 8080) {
                // [V1.0.3] DISABLE ALL SIZE LIMITS
                install(ContentNegotiation) { json() }
                install(WebSockets) { 
                    maxFrameSize = Long.MAX_VALUE 
                }
                
                // Allow massive uploads via multipart
                routing {
                    deviceRoutes(this@EchoCoreService, pairingManager)
                    fileRoutes(this@EchoCoreService, fileRepository, serverEventBus, pairingManager, trustManager)
                    pairingRoutes(pairingManager, trustManager, serverEventBus)
                    webSocketRoutes(serverEventBus)
                }
            }.start(wait = false)
            
            lastNettyPulse = System.currentTimeMillis()
            Log.i("EchoCoreService", "Netty Engine Restarted on 8080")
        } catch (e: Exception) {
            Log.e("EchoCoreService", "Netty Ignition Failed: ${e.message}")
        } finally {
            restartMutex.unlock()
        }
    }

    private suspend fun restartDiscoveryServer() {
        restartMutex.lock()
        try {
            nsdHelper.unregisterServiceGracefully()
            delay(1000) // Settle time
            nsdHelper.registerService(8080)
            lastNsdPulse = System.currentTimeMillis()
            Log.i("EchoCoreService", "NSD Discovery Stack Re-initialized")
        } catch (e: Exception) {
            Log.e("EchoCoreService", "NSD Ignition Failed: ${e.message}")
        } finally {
            restartMutex.unlock()
        }
    }

    // Health pulse update from engines
    fun updatePulse(type: String) {
        if (type == "netty") lastNettyPulse = System.currentTimeMillis()
        if (type == "nsd") lastNsdPulse = System.currentTimeMillis()
    }

    override fun onDestroy() {
        isShuttingDown = true
        activeInstance = null
        try {
            server?.stop(500, 1000)
        } catch (e: Exception) {
            Log.e("EchoCoreService", "Error stopping Netty on termination: ${e.message}")
        }
        serviceScope.cancel()
        engineScope.cancel()
        discoveryScope.cancel()
        nsdHelper.unregisterServiceGracefully()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
