package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.server.ServerEventBus
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun Route.fileRoutes(fileRepository: FileRepository, serverEventBus: ServerEventBus) {
    post("/transfer/upload") {
        val multipart = call.receiveMultipart()
        var fileName = "received_file"
        
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                val file = withContext(Dispatchers.IO) {
                    fileRepository.saveFile(fileName, part.streamProvider())
                }
                serverEventBus.emit(ServerEvent.TransferCompleted(fileName))
            }
            part.dispose()
        }
        call.respondText("Upload successful")
    }
}
