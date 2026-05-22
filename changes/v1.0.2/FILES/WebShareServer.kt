package com.echosystem.localshare.web

import android.content.Context
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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

    fun start() {
        if (server != null) return
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        server = embeddedServer(Netty, port = port) {
            install(WebSockets)

            routing {
                get("/") {
                    call.respondText(loadDashboardHtml(), ContentType.Text.Html)
                }

                // FIXED 5: Increased upload limit logic via streaming multipart
                post("/upload") {
                    try {
                        val multipart = call.receiveMultipart()
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName ?: "upload_${System.currentTimeMillis()}"
                                val targetFile = File(getReceivedDirectory(), fileName)
                                
                                part.streamProvider().use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        // 128KB buffer for High-Speed LAN throughput
                                        val buffer = ByteArray(1024 * 128) 
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                            part.dispose()
                        }
                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) {
                        Log.e("WebShareServer", "Upload error: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Upload failed")
                    }
                }

                // Health check for watchdog
                get("/health") {
                    call.respond(HttpStatusCode.OK, "OK")
                }

                webSocket("/ws") {
                    activeSessions.add(this)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) { /* Ping */ }
                        }
                    } finally {
                        activeSessions.remove(this)
                    }
                }
            }
        }.start(wait = false)
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
            "<html><body><h1>Portal Engine Online</h1></body></html>"
        }
    }
}
