package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class PermissionUpdateRequest(
    val targetDeviceId: String,
    val permissions: List<String>
)

fun Route.managementRoutes(
    trustManager: TrustManager,
    pairingManager: PairingManager
) {
    post("/web/manage/permissions") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        
        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@post
        }

        // Only allow if this user has MANAGE_PERMISSIONS on this device
        if (deviceId.isNotEmpty() && !trustManager.hasPermission(deviceId, DevicePermission.MANAGE_PERMISSIONS)) {
            call.respond(HttpStatusCode.Forbidden, "Insufficient Permissions to manage others")
            return@post
        }

        try {
            val request = call.receive<PermissionUpdateRequest>()
            val perms = request.permissions.mapNotNull { 
                runCatching { DevicePermission.valueOf(it) }.getOrNull() 
            }.toSet()
            
            trustManager.setDevicePermissions(request.targetDeviceId, perms)
            call.respond(HttpStatusCode.OK, "Permissions Updated")
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Invalid Request: ${e.message}")
        }
    }

    get("/web/manage/trusted") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        
        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return@get
        }

        // Only allow if this user has MANAGE_PERMISSIONS on this device
        if (deviceId.isNotEmpty() && !trustManager.hasPermission(deviceId, DevicePermission.MANAGE_PERMISSIONS)) {
            call.respond(HttpStatusCode.Forbidden, "Insufficient Permissions")
            return@get
        }

        call.respond(trustManager.trustedDevices.value)
    }
}
