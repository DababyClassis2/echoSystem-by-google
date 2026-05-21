package com.example.server.routes

import com.example.server.ServerEventBus
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketRoutes @Inject constructor(
    private val serverEventBus: ServerEventBus
) {
    fun Route.registerWebSocketRoutes() {
        webSocket("/ws") {
            try {
                serverEventBus.events.collectLatest { event ->
                    send(Frame.Text(Json.encodeToString(event)))
                }
            } catch (e: Exception) {
                // Connection closed
            }
        }
    }
}
