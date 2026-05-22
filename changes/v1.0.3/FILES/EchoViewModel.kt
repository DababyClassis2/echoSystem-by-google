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
    private val httpClient: HttpClient, // Shared client
    private val serverEventBus: ServerEventBus,
    val coreSystemSupervisor: CoreSystemSupervisor
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList
    
    private val _transferProgress = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferProgress: StateFlow<List<FileTransfer>> = _transferProgress.asStateFlow()

    // [V1.0.3] Event Deduplication Logic
    private val handledEventMap = ConcurrentHashMap<String, Long>()

    init {
        // [V1.0.3] Central Event Collector with deduplication
        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                if (isDuplicate(event)) return@collect
                processEvent(event)
            }
        }
    }

    private fun isDuplicate(event: ServerEvent): Boolean {
        val key = when (event) {
            is ServerEvent.TransferStarted -> "start_${event.fileId}"
            is ServerEvent.TransferCompleted -> "done_${event.fileId}"
            is ServerEvent.TransferFailed -> "fail_${event.fileId}"
            else -> return false
        }
        val now = System.currentTimeMillis()
        val lastTime = handledEventMap[key] ?: 0L
        if (now - lastTime < 1500) return true // 1.5s window for event cleanup
        handledEventMap[key] = now
        return false
    }

    private fun processEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.TransferStarted -> {
                _transferProgress.update { current ->
                    if (current.any { it.id == event.fileId }) return@update current
                    current + FileTransfer(
                        id = event.fileId,
                        fileName = event.fileName,
                        size = event.size,
                        status = TransferStatus.ONGOING,
                        isIncoming = true,
                        remoteDeviceName = "Nearby Portal"
                    )
                }
            }
            is ServerEvent.TransferCompleted -> {
                _transferProgress.update { current ->
                    current.map { 
                        if (it.id == event.fileId) it.copy(status = TransferStatus.COMPLETED, progress = 1f) 
                        else it 
                    }
                }
                loadReceivedFiles() 
            }
            is ServerEvent.TransferFailed -> {
                _transferProgress.update { current ->
                    current.map { if (it.id == event.fileId) it.copy(status = TransferStatus.FAILED) else it }
                }
            }
            is ServerEvent.TransferProgress -> {
                // Throttle UI progress updates (max 10 per second per file)
                _transferProgress.update { current ->
                    current.map {
                        if (it.id == event.fileId && Math.abs(it.progress - event.progress) > 0.05f) {
                            it.copy(progress = event.progress)
                        } else it
                    }
                }
            }
            else -> {}
        }
    }

    fun startDiscovery() {
        viewModelScope.launch {
            // Robust collection from discovery flow
            nsdHelper.discoverDevices()
                .onStart { Log.d("EchoViewModel", "Scanning for peers...") }
                .catch { e -> Log.e("EchoViewModel", "Scan error: ${e.message}") }
                .collect { candidate ->
                    deviceRegistry.addCandidate(candidate)
                }
        }
    }

    fun loadReceivedFiles() {
        viewModelScope.launch {
            try {
                val files = fileRepository.getReceivedFiles()
                // Update history state without destroying ongoing transfer metadata
                _transferProgress.update { existing ->
                    val ongoing = existing.filter { it.status == TransferStatus.ONGOING }
                    val completed = files.map { file ->
                        FileTransfer(
                            id = file.name,
                            fileName = file.name,
                            size = file.length(),
                            progress = 1f,
                            status = TransferStatus.COMPLETED,
                            isIncoming = true,
                            remoteDeviceName = "Stored"
                        )
                    }
                    // Union based on filename
                    val combined = (ongoing + completed).distinctBy { it.fileName }
                    combined
                }
            } catch (e: Exception) {
                AppLogger.logEvent("EchoViewModel", "History Load Fail: ${e.message}")
            }
        }
    }
}
