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
import com.echosystem.localshare.server.routes.*
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class EchoCoreService : Service() {

    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var pairingManager: PairingManager
    @Inject lateinit var trustManager: TrustManager
    @Inject lateinit var serverEventBus: ServerEventBus

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nettyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastNettyRestart = 0L
    private var lastNsdRestart = 0L

    companion object {
        private var activeInstance: EchoCoreService? = null
        fun getInstance(): EchoCoreService? = activeInstance
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        
        // CRITICAL FIX: startForeground() must be called IMMEDIATELY before ANY other work
        startForegroundServiceNotification()
        
        // Use a separate scope and delay to ensure the system processes the foreground transition
        serviceScope.launch {
            delay(100)
            startKtorServer()
            startNsdService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure we are in foreground if restarted
        startForegroundServiceNotification()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "echo_core_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "EchoCore Sync Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keep background synchronization active"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LocalShare Core Active")
            .setContentText("Local sharing sync engine is online.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("EchoCoreService", "Failed to start foreground service: ${e.message}")
        }
    }

    @Synchronized
    fun startKtorServer() {
        // Isolate Netty in its own scope to prevent freezing the main service scope
        nettyScope.launch {
            try {
                if (server != null) {
                    try {
                        server?.stop(500, 1000)
                    } catch (e: Exception) {
                        Log.e("EchoCoreService", "Error stopping Netty: ${e.message}")
                    }
                    server = null
                }
                server = embeddedServer(Netty, port = 8080) {
                    install(ContentNegotiation) {
                        json()
                    }
                    install(WebSockets) {
                        maxFrameSize = Long.MAX_VALUE
                    }
                    routing {
                        deviceRoutes(this@EchoCoreService, pairingManager)
                        fileRoutes(this@EchoCoreService, fileRepository, serverEventBus, pairingManager, trustManager)
                        pairingRoutes(pairingManager, trustManager, serverEventBus)
                        webSocketRoutes(serverEventBus)
                    }
                }.start(wait = false)
                Log.d("EchoCoreService", "Netty Ktor server started successfully on port 8080")
            } catch (e: Exception) {
                Log.e("EchoCoreService", "Failed to start Ktor server: ${e.localizedMessage}")
            }
        }
    }

    @Synchronized
    fun startNsdService() {
        serviceScope.launch {
            try {
                // Ensure helper is clean before registering
                nsdHelper.unregisterServiceGracefully()
                nsdHelper.registerService(8080)
            } catch (e: Exception) {
                Log.e("EchoCoreService", "Failed to register NSD service: ${e.localizedMessage}")
            }
        }
    }

    fun restartNetty() {
        val now = System.currentTimeMillis()
        if (now - lastNettyRestart < 15000) return // Increased frequency check safeguard
        lastNettyRestart = now
        
        Log.w("EchoCoreService", "Restarting Netty server isolation scope...")
        nettyScope.cancel()
        nettyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startKtorServer()
    }

    fun restartNsd() {
        val now = System.currentTimeMillis()
        if (now - lastNsdRestart < 15000) return
        lastNsdRestart = now
        
        Log.w("EchoCoreService", "Force re-triggering NSD registration...")
        startNsdService()
    }

    override fun onDestroy() {
        activeInstance = null
        try {
            server?.stop(500, 1000)
        } catch (e: Exception) {
            Log.e("EchoCoreService", "Error stopping Netty: ${e.message}")
        }
        serviceScope.cancel()
        nettyScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
