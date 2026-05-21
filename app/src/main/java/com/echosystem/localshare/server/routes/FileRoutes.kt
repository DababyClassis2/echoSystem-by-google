package com.echosystem.localshare.server.routes

import com.echosystem.localshare.repository.FileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRoutes @Inject constructor(
    private val fileRepository: FileRepository
) {
    fun Route.registerFileRoutes() {
        get("/api/v1/files") {
            call.respond(fileRepository.receivedFiles.value)
        }

        get("/api/v1/files/download/{name}") {
            val name = call.parameters["name"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val file = fileRepository.getFile(name)
            if (file.exists()) {
                call.respondFile(file)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        delete("/api/v1/files/{name}") {
            val name = call.parameters["name"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            if (fileRepository.deleteFile(name)) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        post("/api/v1/files/upload") {
            // Very simple upload for now - just reading entire body
            // In a real app we'd use MultiPartData
            val fileName = call.request.headers["File-Name"] ?: "received_${System.currentTimeMillis()}"
            val inputStream = call.receiveStream()
            fileRepository.saveFile(fileName, inputStream)
            call.respond(HttpStatusCode.OK)
        }

        // Keep the user's specifically requested route too for compatibility
        post("/transfer/upload") {
            val fileName = call.parameters["name"] ?: call.request.headers["File-Name"] ?: "received_${System.currentTimeMillis()}"
            val inputStream = call.receiveStream()
            fileRepository.saveFile(fileName, inputStream)
            call.respond(HttpStatusCode.OK)
        }
        
        // Step 4: Chunked upload placeholder
        put("/api/v1/files/upload-chunk") {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
