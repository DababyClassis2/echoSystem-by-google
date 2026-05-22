package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.model.*
import com.echosystem.localshare.notification.AppNotificationManager
import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.server.ServerEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
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

    private val _pairingPin = MutableStateFlow<String?>(null)
    val pairingPin: StateFlow<String?> = _pairingPin.asStateFlow()

    private val _incomingPairingRequest = MutableStateFlow<ServerEvent.PairingRequest?>(null)
    val incomingPairingRequest = _incomingPairingRequest.asStateFlow()

    private val _ipAddress = MutableStateFlow(pairingManager.getLocalIp())
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _appEventsLog = MutableStateFlow("")
    val appEventsLog: StateFlow<String> = _appEventsLog.asStateFlow()

    private val _appCrashesLog = MutableStateFlow("")
    val appCrashesLog: StateFlow<String> = _appCrashesLog.asStateFlow()

    // Task 3: Deduplication map for events
    private val processedEventIds = ConcurrentHashMap<String, Long>()

    init {
        com.echosystem.localshare.logging.PerformanceMonitor.startMonitoring(viewModelScope)

        generatePairingPin()
        startDiscovery()
        loadReceivedFiles()
        loadLogs()

        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                val eventId = when (event) {
                    is ServerEvent.TransferStarted -> "start_${event.fileId}"
                    is ServerEvent.TransferCompleted -> "comp_${event.fileId}"
                    is ServerEvent.TransferFailed -> "fail_${event.fileId}_${event.error.hashCode()}"
                    is ServerEvent.PairingRequest -> "pair_${event.deviceId}"
                    else -> null
                }

                if (eventId != null) {
                    val lastTime = processedEventIds[eventId] ?: 0L
                    if (System.currentTimeMillis() - lastTime < 1000) return@collect // Ignore rapid duplicates
                    processedEventIds[eventId] = System.currentTimeMillis()
                }

                handleServerEvent(event)
            }
        }
    }

    private fun handleServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.PairingRequest -> {
                _incomingPairingRequest.value = event
                AppLogger.logEvent("EchoViewModel", "Incoming pairing request from: ${event.deviceName}")
            }
            is ServerEvent.TransferStarted -> {
                if (_transferProgress.value.none { it.id == event.fileId }) {
                    val transfer = FileTransfer(
                        id = event.fileId,
                        fileName = event.fileName,
                        size = event.size,
                        status = TransferStatus.ONGOING,
                        isIncoming = true,
                        remoteDeviceName = "Nearby Node"
                    )
                    _transferProgress.update { it + transfer }
                }
            }
            is ServerEvent.TransferProgress -> {
                updateTransferProgress(event.fileId, event.progress)
            }
            is ServerEvent.TransferCompleted -> {
                updateTransferStatus(event.fileId, TransferStatus.COMPLETED)
                _transferProgress.update { list ->
                    list.map { if (it.id == event.fileId) it.copy(progress = 1.0f) else it }
                }
                loadReceivedFiles()
            }
            is ServerEvent.TransferFailed -> {
                updateTransferStatus(event.fileId, TransferStatus.FAILED)
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

    fun generatePairingPin() {
        _pairingPin.value = pairingManager.generatePin()
    }

    fun acceptPairing(event: ServerEvent.PairingRequest) {
        pairingManager.markAsPaired(event.deviceId)
        trustManager.setDeviceTrust(event.deviceId, true)
        _incomingPairingRequest.value = null
    }

    fun rejectPairing(event: ServerEvent.PairingRequest) {
        _incomingPairingRequest.value = null
    }

    fun sendFileToDevice(device: Device, uri: Uri) {
        viewModelScope.launch {
            val fileName = fileRepository.getFileName(uri) ?: "file"
            val fileSize = fileRepository.getFileSize(uri)
            val id = "TX_${System.currentTimeMillis()}"

            val transfer = FileTransfer(id, fileName, fileSize, status = TransferStatus.ONGOING, isIncoming = false, remoteDeviceName = device.name)
            _transferProgress.update { it + transfer }

            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val response: HttpResponse = httpClient.post("http://${device.ip}:${device.port}/transfer/upload") {
                    setBody(MultiPartFormDataContent(formData {
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        })
                    }))
                    onUpload { sent, total ->
                        if (total != null) updateTransferProgress(id, sent.toFloat() / total)
                    }
                }
                if (response.status == HttpStatusCode.OK) updateTransferStatus(id, TransferStatus.COMPLETED)
                else updateTransferStatus(id, TransferStatus.FAILED)
            } catch (e: Exception) {
                updateTransferStatus(id, TransferStatus.FAILED)
            }
        }
    }

    fun sendMultipleFilesToDevice(device: Device, uris: List<Uri>) {
        uris.forEach { sendFileToDevice(device, it) }
    }

    fun loadReceivedFiles() {
        viewModelScope.launch {
            val files = fileRepository.getReceivedFiles()
            val newCompleted = files.map { file ->
                FileTransfer(file.name, file.name, file.length(), progress = 1.0f, status = TransferStatus.COMPLETED, isIncoming = true, remoteDeviceName = "Nearby Node")
            }
            _transferProgress.update { current ->
                val ongoing = current.filter { it.status == TransferStatus.ONGOING }
                (ongoing + newCompleted).distinctBy { it.fileName }
            }
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            _appEventsLog.value = AppLogger.readEventsLog()
        }
    }

    private fun updateTransferProgress(id: String, progress: Float) {
        _transferProgress.update { list ->
            list.map { if (it.id == id || it.fileName == id) it.copy(progress = progress) else it }
        }
    }

    private fun updateTransferStatus(id: String, status: TransferStatus) {
        _transferProgress.update { list ->
            list.map { if (it.id == id || it.fileName == id) it.copy(status = status) else it }
        }
    }
}
