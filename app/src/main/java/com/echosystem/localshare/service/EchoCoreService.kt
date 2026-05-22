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
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.server.ServerEventBus
import com.echosystem.localshare.server.routes.deviceRoutes
import com.echosystem.localshare.server.routes.fileRoutes
import com.echosystem.localshare.server.routes.managementRoutes
import com.echosystem.localshare.server.routes.pairingRoutes
import com.echosystem.localshare.server.routes.webSocketRoutes
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
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
    private var engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var discoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var lastNettyPulse = 0L
    private var lastNsdPulse = 0L
    private var isShuttingDown = false
    private var isForeground = false

    companion object {
        private const val TAG = "EchoCoreService"
        private var activeInstance: EchoCoreService? = null
        fun getInstance(): EchoCoreService? = activeInstance
        private const val NOTIFICATION_ID = 42
        
        // Helper to consistently start the service
        fun start(context: Context) {
            val intent = Intent(context, EchoCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate: Initializing...")
        AppLogger.logEvent(TAG, "Lifecycle: onCreate triggered")
        
        // [V0.2.3] CRITICAL: FIRST CALL ALWAYS - Secure foreground status
        startForegroundSafe()
        
        super.onCreate()
        activeInstance = this
        
        // Start sub-engines
        igniteEngines()
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v(TAG, "onStartCommand: flags=$flags, startId=$startId")
        
        // [V0.2.3] CRITICAL: Re-assert foreground in case system restarted the service
        startForegroundSafe()
        
        return START_STICKY
    }

    @Synchronized
    private fun startForegroundSafe() {
        if (isForeground) return
        
        val channelId = "echo_core_v023"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "LocalShare Portal Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the local file portal active in the background"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LocalShare Port Active")
            .setContentText("Local portal engine is running.")
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
            isForeground = true
            Log.i(TAG, "Service is now in FOREGROUND state")
            AppLogger.logEvent(TAG, "Foreground transition successful")
        } catch (e: Exception) {
            val errorMsg = "CRITICAL: startForeground failed - ${e.message}"
            Log.e(TAG, errorMsg)
            AppLogger.logEvent(TAG, errorMsg)
            // If this fails, the system will eventually crash with RemoteServiceException
        }
    }

    private fun igniteEngines() {
        engineScope.launch { restartNettyServer() }
        discoveryScope.launch { restartDiscoveryServer() }
    }

    private fun startWatchdog() {
        serviceScope.launch {
            while (!isShuttingDown) {
                delay(30000)
                
                val now = System.currentTimeMillis()
                
                // Netty Health Check via Pulse
                if (lastNettyPulse > 0 && now - lastNettyPulse > 45000) {
                    val msg = "SERVER_WATCHDOG: Netty pulse lost (45s). Reviving Netty engine..."
                    Log.w(TAG, msg)
                    AppLogger.logEvent(TAG, msg)
                    engineScope.launch { restartNettyServer() }
                }

                // NSD Health Check via Pulse
                if (lastNsdPulse > 0 && now - lastNsdPulse > 60000) {
                    val msg = "NSD_WATCHDOG: Discovery pulse lost (60s). Reviving NSD engine..."
                    Log.w(TAG, msg)
                    AppLogger.logEvent(TAG, msg)
                    discoveryScope.launch { restartDiscoveryServer() }
                }
            }
        }
    }

    private val restartMutex = Mutex()

    fun restartNetty() {
        engineScope.launch { restartNettyServer() }
    }

    fun restartNsd() {
        discoveryScope.launch { restartDiscoveryServer() }
    }

    private suspend fun restartNettyServer() {
        restartMutex.lock()
        try {
            server?.stop(500, 1000)
            server = null
            
            server = embeddedServer(Netty, port = 8080) {
                install(ContentNegotiation) { json() }
                install(WebSockets) { maxFrameSize = Long.MAX_VALUE }
                
                routing {
                    get("/health") {
                        updatePulse("netty")
                        call.respondText("OK")
                    }
                    deviceRoutes(this@EchoCoreService, pairingManager)
                    fileRoutes(this@EchoCoreService, fileRepository, serverEventBus, pairingManager, trustManager)
                    pairingRoutes(pairingManager, trustManager, serverEventBus)
                    webSocketRoutes(serverEventBus)
                    managementRoutes(trustManager, pairingManager)
                }
            }.start(wait = false)
            
            updatePulse("netty")
            Log.i(TAG, "Netty engine successfully operational on 8080")
            AppLogger.logEvent(TAG, "Netty engine restart complete")
        } catch (e: Exception) {
            Log.e(TAG, "Netty Ignition Failure: ${e.message}")
            AppLogger.logEvent(TAG, "Netty ignition failed: ${e.message}")
        } finally {
            restartMutex.unlock()
        }
    }

    private suspend fun restartDiscoveryServer() {
        restartMutex.lock()
        try {
            nsdHelper.unregisterServiceGracefully()
            delay(1000)
            nsdHelper.registerService(8080)
            updatePulse("nsd")
            Log.i(TAG, "NSD discovery engine operational")
            AppLogger.logEvent(TAG, "NSD discovery restart complete")
        } catch (e: Exception) {
            Log.e(TAG, "NSD Ignition Failure: ${e.message}")
            AppLogger.logEvent(TAG, "NSD ignition failed: ${e.message}")
        } finally {
            restartMutex.unlock()
        }
    }

    fun updatePulse(type: String) {
        val now = System.currentTimeMillis()
        if (type == "netty") lastNettyPulse = now
        if (type == "nsd") lastNsdPulse = now
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: Shutting down engine...")
        AppLogger.logEvent(TAG, "Lifecycle: onDestroy triggered")
        isShuttingDown = true
        activeInstance = null
        isForeground = false
        
        try {
            server?.stop(500, 1000)
        } catch (e: Exception) { }
        
        serviceScope.cancel()
        engineScope.cancel()
        discoveryScope.cancel()
        nsdHelper.unregisterServiceGracefully()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
