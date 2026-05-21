package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.DeviceInfoResponse
import com.echosystem.localshare.model.DeviceInfoProvider
import com.echosystem.localshare.repository.DeviceRegistry
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRoutes @Inject constructor(
    private val deviceInfoProvider: DeviceInfoProvider,
    private val deviceRegistry: DeviceRegistry
) {
    fun Route.registerDeviceRoutes() {
        get("/api/v1/info") {
            call.respond(DeviceInfoResponse(
                id = deviceInfoProvider.id,
                name = deviceInfoProvider.name,
                port = deviceInfoProvider.port
            ))
        }
        
        get("/api/v1/devices") {
            call.respond(deviceRegistry.devices.value)
        }
    }
}
