package com.echosystem.localshare.web

import android.content.Context
import com.echosystem.localshare.service.EchoCoreService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import android.util.Log

class WebShareServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val port = 8989
    private var serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeSessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketServerSession>())

    fun getActiveSessionIps(): List<String> {
        return activeSessions.mapNotNull { session ->
            session.call.request.origin.remoteHost
        }.distinct()
    }

    fun start() {
        if (server != null) return
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // [V1.0.3] High Performance Configuration
        server = embeddedServer(Netty, port = port) {
            
            // Explicitly handle large bodies
            install(WebSockets)

            routing {
                get("/") {
                    call.respondText(loadDashboardHtml(), ContentType.Text.Html)
                    EchoCoreService.getInstance()?.updatePulse("netty") // Pulse on activity
                }

                get("/health") {
                    call.respond(HttpStatusCode.OK, "Portal Online")
                    EchoCoreService.getInstance()?.updatePulse("netty")
                }

                // Streaming multipart upload with NO limits
                post("/upload") {
                    try {
                        val multipart = call.receiveMultipart()
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName ?: "web_upload_${System.currentTimeMillis()}"
                                val targetFile = File(getReceivedDirectory(), fileName)
                                
                                part.streamProvider().use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        // 256KB buffer for ultra-high speed local parity
                                        val buffer = ByteArray(1024 * 256) 
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            EchoCoreService.getInstance()?.updatePulse("netty")
                                        }
                                    }
                                }
                            }
                            part.dispose()
                        }
                        call.respond(HttpStatusCode.OK, "Success")
                    } catch (e: Exception) {
                        Log.e("WebShareServer", "Portal Upload Crash: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Engine bottleneck or IO error")
                    }
                }

                webSocket("/ws") {
                    activeSessions.add(this)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                EchoCoreService.getInstance()?.updatePulse("netty")
                            }
                        }
                    } finally {
                        activeSessions.remove(this)
                    }
                }
            }
        }.start(wait = false)
        Log.i("WebShareServer", "Portal Netty Engine ignited on $port")
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
        serverScope.cancel()
    }

    private fun getReceivedDirectory(): File {
        val dir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadDashboardHtml(): String {
        return try {
            context.assets.open("web/dashboard.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body style='background:#000;color:#fff;'><h1>Engine Online</h1><p>Portal Frontend missing.</p></body></html>"
        }
    }
}
