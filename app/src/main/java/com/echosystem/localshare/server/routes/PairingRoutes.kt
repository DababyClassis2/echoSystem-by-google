package com.echosystem.localshare.server.routes

import com.echosystem.localshare.security.PairingManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class PairingRequest(val deviceId: String, val pin: String)

fun Route.pairingRoutes(pairingManager: PairingManager) {
    post("/pairing/request") {
        val request = call.receive<PairingRequest>()
        if (pairingManager.verifyPin(request.pin)) {
            pairingManager.markAsPaired(request.deviceId)
            call.respond(HttpStatusCode.OK, "Paired")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
        }
    }
}
