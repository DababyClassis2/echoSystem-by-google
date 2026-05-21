package com.echosystem.localshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.echosystem.localshare.R
import com.echosystem.localshare.model.DeviceInfoProvider
import com.echosystem.localshare.server.routes.DeviceRoutes
import com.echosystem.localshare.server.routes.FileRoutes
import com.echosystem.localshare.server.routes.PairingRoutes
import com.echosystem.localshare.server.routes.WebSocketRoutes
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FileTransferService : Service() {

    @Inject lateinit var deviceInfoProvider: DeviceInfoProvider
    @Inject lateinit var deviceRoutes: DeviceRoutes
    @Inject lateinit var fileRoutes: FileRoutes
    @Inject lateinit var pairingRoutes: PairingRoutes
    @Inject lateinit var webSocketRoutes: WebSocketRoutes

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: NettyApplicationEngine? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): FileTransferService = this@FileTransferService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("EchoSystem is ready to receive and share files.")
        startForeground(NOTIFICATION_ID, notification)
        
        if (server == null) {
            startKtorServer()
        }
        
        return START_STICKY
    }

    private fun startKtorServer() {
        serviceScope.launch {
            try {
                server = embeddedServer(Netty, port = deviceInfoProvider.port) {
                    install(ContentNegotiation) {
                        json()
                    }
                    install(CORS) {
                        anyHost()
                    }
                    routing {
                        with(deviceRoutes) { registerDeviceRoutes() }
                        with(fileRoutes) { registerFileRoutes() }
                        with(pairingRoutes) { registerPairingRoutes() }
                        with(webSocketRoutes) { registerWebSocketRoutes() }
                    }
                }.start(wait = false)
                Log.d("EchoServer", "Ktor server started on port ${deviceInfoProvider.port}")
            } catch (e: Exception) {
                Log.e("EchoServer", "Failed to start Ktor server", e)
            }
        }
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EchoSystem Transfer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps connection pipelines live and allows seamless back-to-back fast file transfers."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EchoSystem Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "EchoSystem_Transfer_Channel"
        private const val NOTIFICATION_ID = 1118
    }
}
