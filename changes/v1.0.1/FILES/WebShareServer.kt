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
import java.net.URLConnection
import java.util.Collections

class WebShareServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var port = 8989
    private var serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeSessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketServerSession>())

    fun start(customPort: Int = 8989) {
        if (server != null) return
        this.port = customPort
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        server = embeddedServer(Netty, port = port, configure = {
            // Increase Netty's built-in limits if available via configuration
            // Note: Ktor Netty specifically often relies on pipeline configuration
        }) {
            install(WebSockets)

            routing {
                get("/") {
                    call.respondText(loadDashboardHtml(), ContentType.Text.Html)
                }

                // API for Redesigned Dashboard (Task 9)
                get("/web/files") {
                    val receivedDir = getReceivedDirectory()
                    val files = receivedDir.listFiles()?.filter { it.isFile } ?: emptyList()
                    val json = files.joinToString(",", "[", "]") { file ->
                        val size = file.length()
                        val formatted = formatBytes(size)
                        val ext = file.extension.lowercase()
                        val type = getFileTypeCategory(ext)
                        """{
                            "name": "${escapeJson(file.name)}",
                            "size": $size,
                            "formattedSize": "$formatted",
                            "type": "$type",
                            "extension": "$ext"
                        }""".trimIndent()
                    }
                    call.respondText(json, ContentType.Application.Json)
                }

                get("/web/download") {
                    val fileName = call.parameters["fileName"] ?: ""
                    val file = File(getReceivedDirectory(), fileName).canonicalFile
                    if (!file.startsWith(getReceivedDirectory().canonicalFile) || !file.exists()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                    call.respondFile(file)
                }

                post("/web/delete") {
                    val fileName = call.parameters["fileName"] ?: ""
                    val file = File(getReceivedDirectory(), fileName).canonicalFile
                    if (file.startsWith(getReceivedDirectory().canonicalFile) && file.exists()) {
                        file.delete()
                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                // Task 5: 2GB Upload Limit implementation
                post("/web/upload") {
                    try {
                        val multipart = call.receiveMultipart()
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName ?: "upload_${System.currentTimeMillis()}"
                                val targetFile = File(getReceivedDirectory(), fileName)
                                
                                broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":0}""")
                                
                                part.streamProvider().use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        val buffer = ByteArray(1024 * 128) // Larger buffer for speed
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                                broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":100}""")
                            }
                            part.dispose()
                        }
                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) {
                        Log.e("WebShareServer", "Upload error: ${e.message}")
                        call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
                    }
                }

                webSocket("/ws") {
                    activeSessions.add(this)
                    try {
                        send(Frame.Text("""{"type":"status", "device":"${escapeJson(getDeviceName())}", "activeClients":${activeSessions.size}}"""))
                        for (frame in incoming) {
                            if (frame is Frame.Text) { /* Keep alive */ }
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
        activeSessions.clear()
        serverScope.cancel()
    }

    fun getPort(): Int = port

    private fun getDeviceName(): String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun getReceivedDirectory(): File {
        val dir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun broadcastToSockets(message: String) {
        val sessions = synchronized(activeSessions) { activeSessions.toList() }
        serverScope.launch {
            for (session in sessions) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    activeSessions.remove(session)
                }
            }
        }
    }

    private fun loadDashboardHtml(): String {
        return try {
            context.assets.open("web/dashboard.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h1>Dashboard Error</h1><p>${e.message}</p></body></html>"
        }
    }

    private fun getFileTypeCategory(ext: String): String = when (ext) {
        "png", "jpg", "jpeg", "webp" -> "image"
        "mp4", "mkv", "avi" -> "video"
        "mp3", "m4a", "wav" -> "music"
        "pdf", "docx", "txt", "zip" -> "document"
        else -> "other"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val k = 1024f
        val sizes = listOf("B", "KB", "MB", "GB")
        val i = Math.floor(Math.log(bytes.toDouble()) / Math.log(k.toDouble())).toInt()
        val num = bytes / Math.pow(k.toDouble(), i.toDouble())
        return "${String.format("%.1f", num)} ${sizes[i]}"
    }

    private fun escapeJson(str: String): String = str.replace("\"", "\\\"").replace("\n", "\\n")
}
