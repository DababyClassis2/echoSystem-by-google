package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.server.ServerEventBus
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes(serverEventBus: ServerEventBus) {
    webSocket("/events") {
        try {
            serverEventBus.events.collect { event ->
                val jsonStr = when (event) {
                    is ServerEvent.DeviceOnline -> """{"type":"device_online","deviceId":"${event.deviceId}","ip":"${event.ip}"}"""
                    is ServerEvent.DeviceOffline -> """{"type":"device_offline","deviceId":"${event.deviceId}"}"""
                    is ServerEvent.FileChanged -> """{"type":"file_changed","event":"${event.event}","path":"${event.path}"}"""
                    is ServerEvent.TransferProgress -> """{"type":"transfer_progress","fileId":"${event.fileId}","progress":${event.progress}}"""
                    is ServerEvent.TransferStarted -> """{"type":"transfer_started","fileId":"${event.fileId}","fileName":"${event.fileName}","size":${event.size}}"""
                    is ServerEvent.TransferCompleted -> """{"type":"transfer_completed","fileId":"${event.fileId}"}"""
                    is ServerEvent.TransferFailed -> """{"type":"transfer_failed","fileId":"${event.fileId}","error":"${event.error}"}"""
                    else -> ""
                }
                if (jsonStr.isNotEmpty()) {
                    send(Frame.Text(jsonStr))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
