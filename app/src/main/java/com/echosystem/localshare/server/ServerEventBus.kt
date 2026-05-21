package com.echosystem.localshare.server

import com.echosystem.localshare.model.ServerEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    suspend fun emit(event: ServerEvent) {
        _events.emit(event)
    }
    
    fun tryEmit(event: ServerEvent) {
        _events.tryEmit(event)
    }
}
