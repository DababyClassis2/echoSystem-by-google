package com.example.server

import com.example.model.ServerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun post(event: ServerEvent) {
        _events.tryEmit(event)
    }
}
