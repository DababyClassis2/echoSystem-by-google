package com.echosystem.localshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FileTransferService : Service() {

    @Inject lateinit var nsdHelper: NsdHelper
    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var pairingManager: PairingManager
    @Inject lateinit var trustManager: TrustManager
    @Inject lateinit var serverEventBus: ServerEventBus

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        startKtorServer()
        nsdHelper.registerService(8080)
    }

    private fun startForegroundService() {
        val channelId = "transfer_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "File Transfer", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EchoSystem LocalShare")
            .setContentText("File server is running")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        
        startForeground(1, notification)
    }

    private fun startKtorServer() {
        server = embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            routing {
                deviceRoutes(this@FileTransferService, pairingManager)
                fileRoutes(fileRepository, serverEventBus, pairingManager)
                pairingRoutes(pairingManager, trustManager)
                webSocketRoutes(serverEventBus)
            }
        }.start(wait = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 2000)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
