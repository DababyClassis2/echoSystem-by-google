package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.DeviceInfoResponse
import com.echosystem.localshare.security.PairingManager
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.deviceRoutes(pairingManager: PairingManager) {
    get("/info") {
        val info = DeviceInfoResponse(
            id = android.provider.Settings.Secure.getString(call.application.contentResolver, android.provider.Settings.Secure.ANDROID_ID),
            name = pairingManager.getDeviceNodeName(),
            port = 8080
        )
        call.respond(info)
    }
}
