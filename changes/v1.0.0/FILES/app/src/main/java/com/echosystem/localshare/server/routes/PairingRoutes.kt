package com.echosystem.localshare.server.routes

import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.server.ServerEventBus
import com.echosystem.localshare.model.ServerEvent
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class PairingRequest(val deviceId: String, val pin: String, val deviceName: String? = null)

fun Route.pairingRoutes(pairingManager: PairingManager, trustManager: TrustManager, serverEventBus: ServerEventBus) {
    post("/pairing/request") {
        val request = call.receive<PairingRequest>()
        val deviceName = request.deviceName ?: "Unknown Web Client"
        val isTrusted = trustManager.isDeviceTrusted(request.deviceId)
        
        // Emit pairing request to ServerEventBus so that ReceiveRadarScreen can prompt Accept/Reject/Block
        serverEventBus.emit(ServerEvent.PairingRequest(request.deviceId, deviceName, request.pin))
        
        if (isTrusted || pairingManager.verifyPin(request.pin)) {
            pairingManager.markAsPaired(request.deviceId)
            call.respond(HttpStatusCode.OK, "Paired")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
        }
    }
    post("/pairing/revoke") {
        val request = call.receive<PairingRequest>()
        pairingManager.revokePairing(request.deviceId)
        call.respond(HttpStatusCode.OK, "Revoked")
    }
}
