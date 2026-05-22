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
    private val port = 8989
    private var serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active WebSocket connection sessions to broadcast real-time syncs
    private val activeSessions = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketServerSession>())

    fun start() {
        if (server != null) return
        serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        server = embeddedServer(Netty, port = port) {
            install(WebSockets)

            routing {
                // 1. Main Home Gateway Dashboard
                get("/") {
                    call.respondText(loadDashboardHtml(), ContentType.Text.Html)
                }

                // 2. Folder Navigation recursive explorer
                get("/files") {
                    val receivedDir = getReceivedDirectory()
                    val reqPath = call.parameters["path"] ?: ""
                    val targetDir = File(receivedDir, reqPath).canonicalFile

                    // Check for directory traversal attacks
                    if (!targetDir.startsWith(receivedDir.canonicalFile)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid directory path requested")
                        return@get
                    }

                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val dirsJson = mutableListOf<String>()
                    val filesJson = mutableListOf<String>()

                    targetDir.listFiles()?.forEach { file ->
                        val relativePath = file.relativeTo(receivedDir).path.replace("\\", "/")
                        if (file.isDirectory) {
                            dirsJson.add("""{"name": "${escapeJson(file.name)}", "path": "${escapeJson(relativePath)}"}""")
                        } else {
                            val size = file.length()
                            val formatted = formatBytes(size)
                            val ext = file.extension.lowercase()
                            val type = getFileTypeCategory(ext)
                            filesJson.add(
                                """{
                                    "name": "${escapeJson(file.name)}", 
                                    "path": "${escapeJson(relativePath)}", 
                                    "size": $size, 
                                    "formattedSize": "$formatted", 
                                    "type": "$type", 
                                    "extension": "$ext"
                                }""".trimIndent()
                            )
                        }
                    }

                    val response = """{
                        "currentPath": "${escapeJson(reqPath)}",
                        "directories": [${dirsJson.joinToString(",")}],
                        "files": [${filesJson.joinToString(",")}]
                    }""".trimIndent()

                    call.respondText(response, ContentType.Application.Json)
                }

                get("/list") {
                    val receivedDir = getReceivedDirectory()
                    val reqPath = call.parameters["path"] ?: ""
                    val targetDir = File(receivedDir, reqPath).canonicalFile

                    // Check for directory traversal attacks
                    if (!targetDir.startsWith(receivedDir.canonicalFile)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid directory path requested")
                        return@get
                    }

                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val dirsJson = mutableListOf<String>()
                    val filesJson = mutableListOf<String>()

                    targetDir.listFiles()?.forEach { file ->
                        val relativePath = file.relativeTo(receivedDir).path.replace("\\", "/")
                        if (file.isDirectory) {
                            dirsJson.add("""{"name": "${escapeJson(file.name)}", "path": "${escapeJson(relativePath)}"}""")
                        } else {
                            val size = file.length()
                            val formatted = formatBytes(size)
                            val ext = file.extension.lowercase()
                            val type = getFileTypeCategory(ext)
                            filesJson.add(
                                """{
                                    "name": "${escapeJson(file.name)}", 
                                    "path": "${escapeJson(relativePath)}", 
                                    "size": $size, 
                                    "formattedSize": "$formatted", 
                                    "type": "$type", 
                                    "extension": "$ext"
                                }""".trimIndent()
                            )
                        }
                    }

                    val response = """{
                        "currentPath": "${escapeJson(reqPath)}",
                        "directories": [${dirsJson.joinToString(",")}],
                        "files": [${filesJson.joinToString(",")}]
                    }""".trimIndent()

                    call.respondText(response, ContentType.Application.Json)
                }

                // 3. File Preview snippet/binary service
                get("/preview") {
                    val receivedDir = getReceivedDirectory()
                    val relativePath = call.parameters["path"] ?: ""
                    val file = File(receivedDir, relativePath).canonicalFile

                    if (!file.startsWith(receivedDir.canonicalFile) || !file.exists() || !file.isFile) {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                        return@get
                    }

                    val ext = file.extension.lowercase()
                    val type = getFileTypeCategory(ext)

                    if (type == "image") {
                        val mimeType = URLConnection.guessContentTypeFromName(file.name) ?: "image/jpeg"
                        call.respondFile(file)
                    } else if (type == "document" && ext in listOf("txt", "log", "json", "xml", "md")) {
                        val snippet = try {
                            file.bufferedReader().use { reader ->
                                val chars = CharArray(1024)
                                val read = reader.read(chars)
                                if (read > 0) String(chars, 0, read) else ""
                            }
                        } catch (e: Exception) {
                            "Error reading preview snippet"
                        }
                        call.respondText(snippet, ContentType.Text.Plain)
                    } else {
                        call.respondText("Preview not supported for this format", ContentType.Text.Plain)
                    }
                }

                // 4. Download Stream handler
                get("/download") {
                    val receivedDir = getReceivedDirectory()
                    val relativePath = call.parameters["path"] ?: ""
                    val file = File(receivedDir, relativePath).canonicalFile

                    if (!file.startsWith(receivedDir.canonicalFile) || !file.exists() || !file.isFile) {
                        call.respond(HttpStatusCode.NotFound, "File not found")
                        return@get
                    }

                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
                    call.respondFile(file)
                }

                // 5. Recursive File Delete handler
                post("/delete") {
                    val receivedDir = getReceivedDirectory()
                    val relativePath = call.parameters["path"] ?: ""
                    val file = File(receivedDir, relativePath).canonicalFile

                    if (!file.startsWith(receivedDir.canonicalFile) || !file.exists()) {
                        call.respond(HttpStatusCode.NotFound, "File or folder not found")
                        return@post
                    }

                    val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (success) {
                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respondText("Successfully deleted resource")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to delete target resource")
                    }
                }

                // 6. Manage creating Folders dynamically
                post("/create-folder") {
                    val receivedDir = getReceivedDirectory()
                    val parentPath = call.parameters["path"] ?: ""
                    val folderName = call.parameters["name"] ?: ""

                    if (folderName.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "Folder name is empty")
                        return@post
                    }

                    val parentDir = File(receivedDir, parentPath).canonicalFile
                    if (!parentDir.startsWith(receivedDir.canonicalFile)) {
                        call.respond(HttpStatusCode.BadRequest, "Access outside boundaries")
                        return@post
                    }

                    val newDir = File(parentDir, folderName)
                    if (newDir.exists()) {
                        call.respond(HttpStatusCode.Conflict, "Directory already exists")
                        return@post
                    }

                    val created = newDir.mkdirs()
                    if (created) {
                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respondText("Folder created securely")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create directory")
                    }
                }

                post("/mkdir") {
                    val receivedDir = getReceivedDirectory()
                    val parentPath = call.parameters["path"] ?: ""
                    val folderName = call.parameters["name"] ?: ""

                    if (folderName.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "Folder name is empty")
                        return@post
                    }

                    val parentDir = File(receivedDir, parentPath).canonicalFile
                    if (!parentDir.startsWith(receivedDir.canonicalFile)) {
                        call.respond(HttpStatusCode.BadRequest, "Access outside boundaries")
                        return@post
                    }

                    val newDir = File(parentDir, folderName)
                    if (newDir.exists()) {
                        call.respond(HttpStatusCode.Conflict, "Directory already exists")
                        return@post
                    }

                    val created = newDir.mkdirs()
                    if (created) {
                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respondText("Folder created securely")
                    } else {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create directory")
                    }
                }

                // 6.5 Query Chunk Upload Status
                get("/upload-status") {
                    val fileId = call.parameters["fileId"] ?: ""
                    val receivedDir = getReceivedDirectory()
                    val tempFiles = receivedDir.listFiles { _, name -> name.startsWith("temp_${fileId}_") } ?: emptyArray()
                    val uploadedIndexes = tempFiles.mapNotNull { file ->
                        file.name.substringAfterLast("_").toIntOrNull()
                    }
                    call.respondText(
                        """{"uploadedChunks": [${uploadedIndexes.joinToString(",")}]}""",
                        ContentType.Application.Json
                    )
                }

                // 7. Secure File Upload with Socket notification triggers
                post("/upload") {
                    val receivedDir = getReceivedDirectory()
                    val isChunked = call.parameters["chunked"] == "true"

                    if (isChunked) {
                        try {
                            val fileId = call.parameters["fileId"] ?: ""
                            val fileName = call.parameters["fileName"] ?: "unknown"
                            val chunkIndex = call.parameters["chunkIndex"]?.toIntOrNull() ?: 0
                            val totalChunks = call.parameters["totalChunks"]?.toIntOrNull() ?: 1
                            val chunkHash = call.parameters["chunkHash"] ?: ""
                            val parentPath = call.parameters["path"] ?: ""

                            val targetDir = File(receivedDir, parentPath).canonicalFile
                            if (!targetDir.startsWith(receivedDir.canonicalFile)) {
                                call.respond(HttpStatusCode.BadRequest, "Access is out of bounds")
                                return@post
                            }

                            val chunkBytes = call.receive<ByteArray>()

                            // SHA-256 Integrity Verification
                            val calculatedHash = chunkBytes.sha256Hex()
                            if (chunkHash.isNotEmpty() && !calculatedHash.equals(chunkHash, ignoreCase = true)) {
                                call.respond(HttpStatusCode.BadRequest, "SHA-256 Verification mismatch for chunk $chunkIndex")
                                return@post
                            }

                            // Save to temp chunk file
                            val tempChunkFile = File(receivedDir, "temp_${fileId}_$chunkIndex")
                            tempChunkFile.outputStream().use { output ->
                                output.write(chunkBytes)
                            }

                            // Check if this was the last chunk and we have all chunks now
                            val tempFiles = receivedDir.listFiles { _, name -> name.startsWith("temp_${fileId}_") } ?: emptyArray()
                            val receivedCount = tempFiles.size
                            
                            val totalProgressPercent = ((chunkIndex + 1).toFloat() / totalChunks * 100).toInt()
                            broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":$totalProgressPercent}""")

                            if (receivedCount == totalChunks) {
                                val destinationFile = File(targetDir, fileName)
                                destinationFile.outputStream().use { output ->
                                    for (i in 0 until totalChunks) {
                                        val chunkFile = File(receivedDir, "temp_${fileId}_$i")
                                        if (chunkFile.exists()) {
                                            chunkFile.inputStream().use { input ->
                                                input.copyTo(output)
                                            }
                                            chunkFile.delete()
                                        }
                                    }
                                }
                                broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":100}""")
                                broadcastToSockets("""{"type":"refresh"}""")
                                call.respondText("""{"completed": true, "status": "Successfully aggregated all chunks"}""", ContentType.Application.Json)
                            } else {
                                call.respondText("""{"completed": false, "status": "Uploaded chunk $chunkIndex successfully"}""", ContentType.Application.Json)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            call.respond(HttpStatusCode.InternalServerError, e.localizedMessage ?: "Failed chunked upload")
                        }
                        return@post
                    }

                    val relativePath = call.parameters["path"] ?: ""
                    val targetDir = File(receivedDir, relativePath).canonicalFile

                    if (!targetDir.startsWith(receivedDir.canonicalFile)) {
                        call.respond(HttpStatusCode.BadRequest, "Destination folder out of bounds")
                        return@post
                    }

                    try {
                        val multipart = call.receiveMultipart()
                        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 1L

                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                                val targetFile = File(targetDir, fileName)

                                broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":0}""")

                                part.streamProvider().use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        val buffer = ByteArray(1024 * 64)
                                        var totalBytesRead = 0L
                                        var lastProgressTime = 0L

                                        var readBytes = input.read(buffer)
                                        while (readBytes >= 0) {
                                            output.write(buffer, 0, readBytes)
                                            totalBytesRead += readBytes

                                            val now = System.currentTimeMillis()
                                            if (now - lastProgressTime > 150) {
                                                val progress = (totalBytesRead.toFloat() / contentLength * 100).toInt()
                                                broadcastToSockets(
                                                    """{"type":"progress", "file":"${escapeJson(fileName)}", "progress":$progress}"""
                                                )
                                                lastProgressTime = now
                                            }
                                            readBytes = input.read(buffer)
                                        }
                                    }
                                }
                                broadcastToSockets("""{"type":"progress", "file":"${escapeJson(fileName)}", "progress":100}""")
                            }
                            part.dispose()
                        }

                        broadcastToSockets("""{"type":"refresh"}""")
                        call.respondText("Successfully uploaded")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        call.respond(HttpStatusCode.InternalServerError, e.localizedMessage ?: "Failed raw upload")
                    }
                }

                // 8. Dynamic WebSocket Event system for instant reactivity
                webSocket("/ws") {
                    activeSessions.add(this)
                    try {
                        // Immediately respond with active connection status to the new client
                        send(Frame.Text("""{"type":"status", "device":"${escapeJson(getDeviceName())}", "activeClients":${activeSessions.size}}"""))
                        // Broadcast update to all other connected clients
                        broadcastToSockets("""{"type":"status", "device":"${escapeJson(getDeviceName())}", "activeClients":${activeSessions.size}}""")

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                // Process client ping or commands if any
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        activeSessions.remove(this)
                        // Broadcast the updated connection state to the remaining clients
                        broadcastToSockets("""{"type":"status", "device":"${escapeJson(getDeviceName())}", "activeClients":${activeSessions.size}}""")
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        activeSessions.clear()
        try {
            serverScope.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Android"
        val model = android.os.Build.MODEL ?: "Device"
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    private fun getReceivedDirectory(): File {
        val downloadDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return downloadDir
    }

    private fun broadcastToSockets(message: String) {
        val sessionsCopy = synchronized(activeSessions) { activeSessions.toList() }
        serverScope.launch {
            for (session in sessionsCopy) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    // remove stale session automatically if sending failed
                    activeSessions.remove(session)
                }
            }
        }
    }

    private fun loadDashboardHtml(): String {
        return try {
            context.assets.open("web/dashboard.html")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            FALLBACK_DASHBOARD_HTML
        }
    }

    private fun getFileTypeCategory(ext: String): String {
        return when (ext) {
            "png", "jpg", "jpeg", "webp", "gif", "svg" -> "image"
            "mp4", "mkv", "mov", "avi", "webm", "3gp" -> "video"
            "mp3", "wav", "m4a", "flac", "ogg" -> "music"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "log", "json", "xml", "csv", "md" -> "document"
            else -> "other"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val k = 1024f
        val sizes = listOf("B", "KB", "MB", "GB", "TB")
        val i = Math.floor(Math.log(bytes.toDouble()) / Math.log(k.toDouble())).toInt()
        val num = bytes / Math.pow(k.toDouble(), i.toDouble())
        return "${String.format("%.1f", num)} ${sizes[i]}"
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\u000C", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
    }

    companion object {
        private const val FALLBACK_DASHBOARD_HTML = """<!DOCTYPE html>
<html>
<head>
    <title>LocalShare Web Gateway</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: sans-serif; padding: 24px; background-color: #0f172a; color: #f1f5f9; margin: 0; text-align: center; }
        .box { padding: 32px; background-color: #1e293b; border-radius: 12px; max-width: 500px; margin: 40px auto; border: 1px solid #334155; }
        h1 { margin-top: 0; color: #6366f1; }
    </style>
</head>
<body>
    <div class="box">
        <h1>LocalShare Web Portal</h1>
        <p>Asset loading failed or was not integrated yet, but your HTTP server is running perfectly! Refreshing the view is recommended.</p>
    </div>
</body>
</html>"""
    }
}

fun ByteArray.sha256Hex(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(this)
    return hash.joinToString("") { String.format("%02x", it) }
}
