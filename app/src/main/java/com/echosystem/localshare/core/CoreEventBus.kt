package com.echosystem.localshare.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreEventBus @Inject constructor() {
    // Pipeline with ample buffer capacity for high-throughput event processing
    private val _events = MutableSharedFlow<CoreEvent>(
        extraBufferCapacity = 128
    )
    val events: SharedFlow<CoreEvent> = _events.asSharedFlow()

    /**
     * Publishes a type-safe dynamic CoreEvent to all active subscribers asynchronously.
     */
    suspend fun emit(event: CoreEvent) {
        _events.emit(event)
    }

    /**
     * Safe non-suspending fallback interface for emit inside background service/UI callbacks.
     */
    fun tryEmit(event: CoreEvent): Boolean {
        return _events.tryEmit(event)
    }
}
