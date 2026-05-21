package com.echosystem.localshare.server.routes

import com.echosystem.localshare.server.ServerEventBus
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes(serverEventBus: ServerEventBus) {
    webSocket("/events") {
        serverEventBus.events.collectLatest { event ->
            send(Frame.Text(Json.encodeToString(event)))
        }
    }
}
