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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        // CRITICAL: Call startForeground IMMEDIATELY
        startForegroundServiceNotification()
        
        // Start dependencies on background threads
        startKtorServer()
        startNsdService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LocalShare Core Active")
            .setContentText("Local sharing sync engine is online.")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    @Synchronized
    fun startKtorServer() {
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
                        // Max frame configuration for bulk performance uploads
                        maxFrameSize = Long.MAX_VALUE
                    }
                    routing {
                        deviceRoutes(this@EchoCoreService, pairingManager)
                        fileRoutes(fileRepository, serverEventBus, pairingManager, trustManager)
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
                nsdHelper.registerService(8080)
            } catch (e: Exception) {
                Log.e("EchoCoreService", "Failed to register NSD service: ${e.localizedMessage}")
            }
        }
    }

    fun restartNetty() {
        val now = System.currentTimeMillis()
        if (now - lastNettyRestart < 60000) {
            Log.d("EchoCoreService", "Netty restart ignored: inside 60s cooldown.")
            return
        }
        lastNettyRestart = now
        Log.d("EchoCoreService", "Watchdog triggered Netty-only restart.")
        nettyScope.cancel()
        nettyScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        startKtorServer()
    }

    fun restartNsd() {
        val now = System.currentTimeMillis()
        if (now - lastNsdRestart < 60000) {
            Log.d("EchoCoreService", "NSD restart ignored: inside 60s cooldown.")
            return
        }
        lastNsdRestart = now
        Log.d("EchoCoreService", "Watchdog triggered NSD-only restart.")
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
