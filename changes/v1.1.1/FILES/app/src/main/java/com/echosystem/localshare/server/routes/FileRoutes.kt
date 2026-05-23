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
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

fun Route.refinedFileRoutes(
    context: Context,
    fileRepository: FileRepository,
    serverEventBus: ServerEventBus,
    pairingManager: PairingManager,
    trustManager: TrustManager
) {
    // Legacy support for web dashboard remained mostly unchanged
    // ...

    // New Refined App-to-App Transfer Route with Integrity Check (Brick 2)
    post("/transfer/upload") {
        val deviceId = call.request.headers["X-Device-Id"] ?: ""
        val expectedChecksum = call.request.headers["X-Checksum"]
        
        if (!pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        try {
            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                    val targetFile = File(fileRepository.getReceivedFolder(), fileName)
                    
                    val digest = MessageDigest.getInstance("SHA-256")
                    
                    withContext(Dispatchers.IO) {
                        part.streamProvider().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(256 * 1024) // 256KB chunks as requested
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    digest.update(buffer, 0, read)
                                }
                            }
                        }
                    }
                    
                    // Integrity Verification
                    if (expectedChecksum != null) {
                        val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
                        if (actualChecksum != expectedChecksum) {
                            targetFile.delete()
                            call.respond(HttpStatusCode.BadRequest, "Integrity check failed: Checksum mismatch")
                            return@forEachPart
                        }
                    }
                    
                    serverEventBus.emit(ServerEvent.TransferCompleted(fileName))
                }
                part.dispose()
            }
            call.respond(HttpStatusCode.OK)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Upload failed")
        }
    }
}
