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
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nettyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastNettyRestart = 0L

    companion object {
        private var activeInstance: EchoCoreService? = null
        fun getInstance(): EchoCoreService? = activeInstance
    }

    override fun onCreate() {
        // RULE 1: startForeground MUST be the first thing
        startForegroundServiceNotification()
        
        super.onCreate()
        activeInstance = this
        
        // Start other components safely after foreground status is established
        serviceScope.launch {
            delay(200) // Small breather for system
            startKtorServer()
            startNsdService()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // RULE 1: startForeground MUST be first
        startForegroundServiceNotification()
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "echo_core_channel_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "EchoCore Core Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Crucial for local file synchronization stability"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LocalShare Core Active")
            .setContentText("Synchronization engine is running in isolation.")
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
            Log.e("EchoCoreService", "CRITICAL: Foreground start failed: ${e.message}")
        }
    }

    @Synchronized
    fun startKtorServer() {
        // Isolated scope for Netty to prevent main thread blocking or broad service failures
        nettyScope.launch {
            try {
                server?.stop(500, 1000)
                server = embeddedServer(Netty, port = 8080) {
                    install(ContentNegotiation) { json() }
                    install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
                    routing {
                        deviceRoutes(this@EchoCoreService, pairingManager)
                        fileRoutes(this@EchoCoreService, fileRepository, serverEventBus, pairingManager, trustManager)
                        pairingRoutes(pairingManager, trustManager, serverEventBus)
                        webSocketRoutes(serverEventBus)
                    }
                }.start(wait = false)
                Log.d("EchoCoreService", "Netty Ktor server (isolation mode) started on 8080")
            } catch (e: Exception) {
                Log.e("EchoCoreService", "Netty startup failed: ${e.localizedMessage}")
                // Auto-retry after failure
                delay(5000)
                restartNetty()
            }
        }
    }

    @Synchronized
    fun startNsdService() {
        serviceScope.launch {
            try {
                nsdHelper.unregisterServiceGracefully()
                nsdHelper.registerService(8080)
            } catch (e: Exception) {
                Log.e("EchoCoreService", "NSD Registration failed: ${e.localizedMessage}")
            }
        }
    }

    fun restartNetty() {
        val now = System.currentTimeMillis()
        if (now - lastNettyRestart < 10000) return 
        lastNettyRestart = now
        
        Log.w("EchoCoreService", "Triggering Netty isolation restart...")
        nettyScope.cancel()
        nettyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startKtorServer()
    }

    override fun onDestroy() {
        activeInstance = null
        try {
            server?.stop(500, 1000)
        } catch (e: Exception) {
            Log.e("EchoCoreService", "Error stopping Netty on destroy: ${e.message}")
        }
        serviceScope.cancel()
        nettyScope.cancel()
        nsdHelper.unregisterServiceGracefully()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
