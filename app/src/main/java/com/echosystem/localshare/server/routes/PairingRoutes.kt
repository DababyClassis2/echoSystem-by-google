package com.echosystem.localshare.server.routes

import com.echosystem.localshare.security.PairingManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class AuthRequest(val pin: String)

@Singleton
class PairingRoutes @Inject constructor(
    private val pairingManager: PairingManager
) {
    fun Route.registerPairingRoutes() {
        post("/api/v1/authenticate") {
            try {
                val request = call.receive<AuthRequest>()
                if (pairingManager.verifyPin(request.pin)) {
                    call.respond(HttpStatusCode.OK, "Authenticated")
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid Request")
            }
        }
    }
}
