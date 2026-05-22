package com.echosystem.localshare.server.routes

import android.content.Context
import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.server.ServerEventBus
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class FileWebResponse(
    val name: String, 
    val size: Long, 
    val formattedSize: String,
    val type: String,
    val extension: String
)

fun Route.fileRoutes(
    context: Context,
    fileRepository: FileRepository,
    serverEventBus: ServerEventBus,
    pairingManager: PairingManager,
    trustManager: TrustManager
) {
    // 1. Serve Mobile-Optimized Web Portal Dashboard
    get("/") {
        try {
            val html = context.assets.open("web/dashboard.html").bufferedReader().use { it.readText() }
            call.respondText(html, ContentType.Text.Html)
        } catch (e: Exception) {
            call.respondText("Portal UI Asset missing. Please check assets/web/dashboard.html", ContentType.Text.Html)
        }
    }

    // 2. Query Available Downloadable Files with Category Metadata
    get("/web/files") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""

        if (trustManager.isDeviceBlocked(deviceId)) {
            call.respond(HttpStatusCode.Forbidden, "Access Blocked")
            return@get
        }

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@get
        }

        val files = fileRepository.getReceivedFiles()
        val response = files.map { file ->
            val size = file.length()
            val formatted = formatBytes(size)
            val ext = file.extension.lowercase()
            val type = getFileTypeCategory(ext)
            FileWebResponse(file.name, size, formatted, type, ext)
        }
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    // 3. Download File Stream
    get("/web/download") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        val fileName = call.parameters["fileName"] ?: ""

        if (trustManager.isDeviceBlocked(deviceId)) {
            call.respond(HttpStatusCode.Forbidden, "Access Blocked")
            return@get
        }

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@get
        }

        if (fileName.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@get
        }

        val receivedDir = fileRepository.getReceivedFilesDir()
        val file = File(receivedDir, fileName)

        if (!file.exists() || !file.isFile) {
            call.respond(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
        call.respondFile(file)
    }

    // 4. Handle Secure Browser Direct API Upload
    post("/web/upload") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""

        if (trustManager.isDeviceBlocked(deviceId)) {
            call.respond(HttpStatusCode.Forbidden, "Access Blocked")
            return@post
        }

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@post
        }

        try {
            val multipart = call.receiveMultipart()
            var fileName = "received_web_file"
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                    
                    val file = withContext(Dispatchers.IO) {
                        val downloadDir = fileRepository.getReceivedFilesDir()
                        val targetFile = File(downloadDir, fileName)

                        serverEventBus.emit(ServerEvent.TransferStarted(fileName, fileName, contentLength))

                        part.streamProvider().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(1024 * 64)
                                var total = 0L
                                var lastNotify = 0L
                                
                                var read = input.read(buffer)
                                while (read >= 0) {
                                    output.write(buffer, 0, read)
                                    total += read
                                    
                                    val now = System.currentTimeMillis()
                                    if (contentLength > 0 && now - lastNotify > 100) {
                                        serverEventBus.emit(ServerEvent.TransferProgress(fileName, total.toFloat() / contentLength))
                                        lastNotify = now
                                    }
                                    read = input.read(buffer)
                                }
                            }
                        }
                        serverEventBus.emit(ServerEvent.TransferCompleted(fileName))
                        targetFile
                    }
                }
                part.dispose()
            }
            call.respondText("OK")
        } catch (e: Exception) {
            serverEventBus.emit(ServerEvent.TransferFailed("Web upload failed", e.localizedMessage ?: "Unknown error"))
            call.respond(HttpStatusCode.InternalServerError, "Upload failed")
        }
    }

    // 5. Delete specific file
    post("/web/delete") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        val fileName = call.parameters["fileName"] ?: ""

        if (trustManager.isDeviceBlocked(deviceId)) {
            call.respond(HttpStatusCode.Forbidden, "Access Blocked")
            return@post
        }

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@post
        }

        if (fileName.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@post
        }

        val deleted = fileRepository.deleteReceivedFile(fileName)
        if (deleted) {
            call.respondText("OK")
        } else {
            call.respond(HttpStatusCode.NotFound, "Not Found")
        }
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
