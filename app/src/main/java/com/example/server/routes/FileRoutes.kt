package com.example.server.routes

import com.example.repository.FileRepository
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
        
        // Step 4: Chunked upload placeholder
        put("/api/v1/files/upload-chunk") {
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
