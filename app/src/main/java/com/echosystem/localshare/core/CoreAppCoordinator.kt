package com.echosystem.localshare.core

import com.echosystem.localshare.logging.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreAppCoordinator @Inject constructor(
    private val eventBus: CoreEventBus,
    private val networkModeManager: NetworkModeManager
) {
    private val tag = "CoreAppCoordinator"

    private val _coreState = MutableStateFlow<CoreState>(CoreState.Idle)
    val coreState: StateFlow<CoreState> = _coreState.asStateFlow()

    suspend fun startCore() {
        AppLogger.logEvent(tag, "Starting system core services...")
        _coreState.value = CoreState.Idle
    }

    suspend fun stopCore() {
        AppLogger.logEvent(tag, "Stopping system core services...")
        _coreState.value = CoreState.Idle
    }

    suspend fun enterWebShareMode() {
        _coreState.value = CoreState.WebShareActive
    }

    suspend fun exitWebShareMode() {
        _coreState.value = CoreState.Idle
    }

    fun startDiscovery() {
        _coreState.value = CoreState.Discovering
    }

    fun stopDiscovery() {
        _coreState.value = CoreState.Idle
    }
}
