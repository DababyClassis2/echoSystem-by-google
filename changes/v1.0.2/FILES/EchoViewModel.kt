package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.model.*
import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.server.ServerEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.core.CoreSystemSupervisor

@HiltViewModel
class EchoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRegistry: DeviceRegistry,
    private val fileRepository: FileRepository,
    private val pairingManager: PairingManager,
    val trustManager: TrustManager,
    private val nsdHelper: NsdHelper,
    private val httpClient: HttpClient,
    private val serverEventBus: ServerEventBus,
    val coreSystemSupervisor: CoreSystemSupervisor
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList
    private val _transferProgress = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferProgress: StateFlow<List<FileTransfer>> = _transferProgress.asStateFlow()

    // FIXED 4: Deduplication logic
    private val processedEvents = ConcurrentHashMap<String, Long>()

    init {
        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                if (shouldThrottle(event)) return@collect
                handleEvent(event)
            }
        }
    }

    private fun shouldThrottle(event: ServerEvent): Boolean {
        val key = when (event) {
            is ServerEvent.TransferStarted -> "start_${event.fileId}"
            is ServerEvent.TransferCompleted -> "done_${event.fileId}"
            is ServerEvent.TransferFailed -> "fail_${event.fileId}"
            else -> return false
        }
        val now = System.currentTimeMillis()
        val last = processedEvents[key] ?: 0L
        if (now - last < 1000) return true
        processedEvents[key] = now
        return false
    }

    private fun handleEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.TransferStarted -> {
                val exists = _transferProgress.value.any { it.id == event.fileId }
                if (!exists) {
                    _transferProgress.update { it + FileTransfer(event.fileId, event.fileName, event.size, TransferStatus.ONGOING, true, "Nearby Node") }
                }
            }
            is ServerEvent.TransferCompleted -> {
                _transferProgress.update { list ->
                    list.map { if (it.id == event.fileId) it.copy(status = TransferStatus.COMPLETED, progress = 1f) else it }
                }
            }
            is ServerEvent.TransferFailed -> {
                _transferProgress.update { list ->
                    list.map { if (it.id == event.fileId) it.copy(status = TransferStatus.FAILED) else it }
                }
            }
            else -> {}
        }
    }

    fun startDiscovery() {
        viewModelScope.launch {
            nsdHelper.discoverDevices().collect { candidate ->
                deviceRegistry.addCandidate(candidate)
            }
        }
    }
}
