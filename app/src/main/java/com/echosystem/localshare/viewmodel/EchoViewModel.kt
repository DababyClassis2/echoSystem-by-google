package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.model.TransferStatus
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

    private val _ipAddress = MutableStateFlow(pairingManager.getLocalIp())
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _appEventsLog = MutableStateFlow("")
    val appEventsLog: StateFlow<String> = _appEventsLog.asStateFlow()

    private val _appCrashesLog = MutableStateFlow("")
    val appCrashesLog: StateFlow<String> = _appCrashesLog.asStateFlow()

    init {
        // Register telemetry providers for System Performance watchdog
        com.echosystem.localshare.logging.PerformanceMonitor.registerSpeedProvider {
            val ongoingCount = _transferProgress.value.count { it.status == TransferStatus.ONGOING }
            Pair(ongoingCount, 0.0)
        }
        com.echosystem.localshare.logging.PerformanceMonitor.startMonitoring(viewModelScope)

        // Automatically generate a default PIN and auto-start network discovery
        generatePairingPin()
        startDiscovery()
        loadReceivedFiles()
        loadLogs()

        // Synchronize with server events for real-time transfers (e.g. from nearby hosts)
        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                when (event) {
                    is ServerEvent.PairingRequest -> {
                        AppLogger.logEvent("EchoViewModel", "Received pairing connection query from client ID: ${event.deviceId}")
                    }
                    is ServerEvent.TransferStarted -> {
                        val exists = _transferProgress.value.any { it.id == event.fileId || it.fileName == event.fileName }
                        if (!exists) {
                            val incomingTransfer = FileTransfer(
                                id = event.fileId,
                                fileName = event.fileName,
                                size = event.size,
                                status = TransferStatus.ONGOING,
                                isIncoming = true,
                                remoteDeviceName = "Nearby Host"
                            )
                            _transferProgress.update { it + incomingTransfer }
                        }
                        AppLogger.logEvent("EchoViewModel", "Incoming transmission starting: ${event.fileName} (${event.size} bytes)")
                    }
                    is ServerEvent.TransferProgress -> {
                        updateTransferProgress(event.fileId, event.progress)
                    }
                    is ServerEvent.TransferCompleted -> {
                        val fileId = event.fileId
                        val exists = _transferProgress.value.any { it.id == fileId || it.fileName == fileId }
                        if (exists) {
                            updateTransferStatus(fileId, TransferStatus.COMPLETED)
                            updateTransferProgress(fileId, 1f)
                        } else {
                            val incomingCompleted = FileTransfer(
                                id = fileId,
                                fileName = fileId,
                                size = 0L,
                                progress = 1f,
                                status = TransferStatus.COMPLETED,
                                isIncoming = true,
                                remoteDeviceName = "Nearby Host"
                            )
                            _transferProgress.update { it + incomingCompleted }
                        }
                        AppNotificationManager.notifyFileReceived(context, fileId, "Local Portal Sync")
                        AppLogger.logEvent("EchoViewModel", "Incoming transfer successfully completed: $fileId")
                        loadReceivedFiles()
                    }
                    is ServerEvent.TransferFailed -> {
                        updateTransferStatus(event.fileId, TransferStatus.FAILED)
                        AppNotificationManager.notifyTransferFailed(context, "Incoming transfer session failed: ${event.error}")
                        AppLogger.logEvent("EchoViewModel", "Incoming transfer failed on ID ${event.fileId}: ${event.error}")
                    }
                }
            }
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

    fun pairWithDevice(device: Device, pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val myDeviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                val response: HttpResponse = httpClient.post("http://${device.ip}:${device.port}/pairing/request") {
                    contentType(ContentType.Application.Json)
                    setBody(com.echosystem.localshare.server.routes.PairingRequest(myDeviceId, pin))
                }
                if (response.status == HttpStatusCode.OK) {
                    pairingManager.markAsPaired(device.id)
                    deviceRegistry.updateDeviceStatus(device.id, com.echosystem.localshare.model.DeviceStatus.CONNECTED)
                    onResult(true, null)
                } else {
                    onResult(false, "Verification failed: Status ${response.status}")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Connection error")
            }
        }
    }

    fun sendFileToDevice(device: Device, uri: Uri) {
        viewModelScope.launch {
            val fileName = fileRepository.getFileName(uri) ?: "unknown_file"
            val fileSize = fileRepository.getFileSize(uri)
            
            val transfer = FileTransfer(
                id = System.currentTimeMillis().toString(),
                fileName = fileName,
                size = fileSize,
                status = TransferStatus.ONGOING,
                isIncoming = false,
                remoteDeviceName = device.name
            )
            
            _transferProgress.update { it + transfer }
            
            val adaptiveBlockSize = coreSystemSupervisor.getAdaptiveBlockSize()
            AppLogger.logEvent("EchoViewModel", "Starting outgoing transfer of $fileName ($fileSize bytes) to ${device.name} - Adaptive buffer chunk allocation size: ${adaptiveBlockSize / 1024} KB")

            try {
                val inputStream = fileRepository.openInputStream(uri) ?: return@launch
                
                // execute within auto-retry and stability failover logic
                val response: HttpResponse = coreSystemSupervisor.runWithAutoRetry(device) { resolvedIp ->
                    httpClient.post("http://$resolvedIp:${device.port}/transfer/upload") {
                        setBody(MultiPartFormDataContent(
                            formData {
                                append("file", inputStream.readBytes(), Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                })
                            }
                        ))
                        onUpload { bytesSentTotal, contentLength ->
                            val progressValue = if (contentLength != null && contentLength > 0L) {
                                bytesSentTotal.toFloat() / contentLength
                            } else {
                                0f
                            }
                            updateTransferProgress(transfer.id, progressValue)
                        }
                    }
                }

                if (response.status == HttpStatusCode.OK) {
                    updateTransferStatus(transfer.id, TransferStatus.COMPLETED)
                    AppNotificationManager.notifyFileSent(context, fileName, "Completed")
                    AppLogger.logEvent("EchoViewModel", "Outgoing transfer successfully completed: $fileName")
                } else {
                    updateTransferStatus(transfer.id, TransferStatus.FAILED)
                    AppNotificationManager.notifyTransferFailed(context, "Sending $fileName failed: Status ${response.status}")
                    AppLogger.logEvent("EchoViewModel", "Outgoing transfer failed for $fileName: Status ${response.status}")
                }
            } catch (e: Exception) {
                updateTransferStatus(transfer.id, TransferStatus.FAILED)
                AppNotificationManager.notifyTransferFailed(context, "Sending $fileName failed: ${e.localizedMessage}")
                AppLogger.logEvent("EchoViewModel", "Outgoing transfer failed for $fileName with exception: ${e.message}")
            }
        }
    }

    fun sendMultipleFilesToDevice(device: Device, uris: List<Uri>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val fileName = fileRepository.getFileName(uri) ?: "unknown_file"
                val fileSize = fileRepository.getFileSize(uri)
                
                val transfer = FileTransfer(
                    id = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
                    fileName = fileName,
                    size = fileSize,
                    status = TransferStatus.ONGOING,
                    isIncoming = false,
                    remoteDeviceName = device.name
                )
                
                _transferProgress.update { it + transfer }
                
                try {
                    val inputStream = fileRepository.openInputStream(uri)
                    if (inputStream == null) {
                        updateTransferStatus(transfer.id, TransferStatus.FAILED)
                        return@forEach
                    }
                    
                    val response: HttpResponse = coreSystemSupervisor.runWithAutoRetry(device) { resolvedIp ->
                        httpClient.post("http://$resolvedIp:${device.port}/transfer/upload") {
                            setBody(MultiPartFormDataContent(
                                formData {
                                    append("file", inputStream.readBytes(), Headers.build {
                                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                    })
                                }
                            ))
                            onUpload { bytesSentTotal, contentLength ->
                                val progressValue = if (contentLength != null && contentLength > 0L) {
                                    bytesSentTotal.toFloat() / contentLength
                                } else {
                                    0f
                                }
                                updateTransferProgress(transfer.id, progressValue)
                            }
                        }
                    }

                    if (response.status == HttpStatusCode.OK) {
                        updateTransferStatus(transfer.id, TransferStatus.COMPLETED)
                        AppNotificationManager.notifyFileSent(context, fileName, "Completed")
                        AppLogger.logEvent("EchoViewModel", "Outgoing transfer successfully completed: $fileName")
                    } else {
                        updateTransferStatus(transfer.id, TransferStatus.FAILED)
                        AppNotificationManager.notifyTransferFailed(context, "Sending $fileName failed: Status ${response.status}")
                        AppLogger.logEvent("EchoViewModel", "Outgoing transfer failed for $fileName: Status ${response.status}")
                    }
                } catch (e: Exception) {
                    updateTransferStatus(transfer.id, TransferStatus.FAILED)
                    AppNotificationManager.notifyTransferFailed(context, "Sending $fileName failed: ${e.localizedMessage}")
                    AppLogger.logEvent("EchoViewModel", "Outgoing transfer failed for $fileName with exception: ${e.message}")
                }
            }
        }
    }

    fun queryFileName(uri: android.net.Uri): String {
        return fileRepository.getFileName(uri) ?: "Selected File"
    }

    fun queryFileSize(uri: android.net.Uri): Long {
        return fileRepository.getFileSize(uri)
    }

    fun clearTransfers() {
        viewModelScope.launch {
            try {
                fileRepository.getReceivedFiles().forEach { file ->
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _transferProgress.value = emptyList()
        }
    }

    fun deleteFileFromHistory(fileName: String) {
        viewModelScope.launch {
            try {
                fileRepository.deleteReceivedFile(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _transferProgress.update { list ->
                list.filterNot { it.fileName == fileName }
            }
        }
    }

    fun loadReceivedFiles() {
        viewModelScope.launch {
            try {
                val files = fileRepository.getReceivedFiles()
                val fileNamesOnDisk = files.map { it.name }.toSet()
                
                _transferProgress.update { currentList ->
                    val ongoingTransfers = currentList.filter { it.status == TransferStatus.ONGOING }
                    val existingCompletedOnDisk = currentList.filter {
                        it.status == TransferStatus.COMPLETED && fileNamesOnDisk.contains(it.fileName)
                    }
                    val knownNames = (ongoingTransfers + existingCompletedOnDisk).map { it.fileName }.toSet()
                    val newTransfers = files.filter { !knownNames.contains(it.name) }.map { file ->
                        FileTransfer(
                            id = file.name,
                            fileName = file.name,
                            size = file.length(),
                            progress = 1f,
                            status = TransferStatus.COMPLETED,
                            isIncoming = true,
                            remoteDeviceName = "Nearby Node"
                        )
                    }
                    ongoingTransfers + existingCompletedOnDisk + newTransfers
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            _appEventsLog.value = AppLogger.readEventsLog()
            _appCrashesLog.value = AppLogger.readCrashesLog()
        }
    }

    fun clearLogsAndRefresh() {
        viewModelScope.launch {
            AppLogger.clearLogs()
            loadLogs()
        }
    }

    fun exportLogsAndRefresh(context: Context) {
        AppLogger.exportLogs(context)
        loadLogs()
    }

    fun addManualLog(tag: String, message: String) {
        AppLogger.logEvent(tag, message)
        loadLogs()
    }

    fun disconnectDevice(device: Device) {
        deviceRegistry.updateDeviceStatus(device.id, com.echosystem.localshare.model.DeviceStatus.DISCONNECTED)
    }

    fun revokeDevice(device: Device) {
        viewModelScope.launch {
            try {
                // Potential remote revocation
                httpClient.post("http://${device.ip}:${device.port}/pairing/revoke") {
                    contentType(ContentType.Application.Json)
                    setBody(com.echosystem.localshare.server.routes.PairingRequest(
                        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID),
                        ""
                    ))
                }
            } catch (e: Exception) {
                AppLogger.logEvent("EchoViewModel", "Remote revoke failed: ${e.message}")
            }
            
            pairingManager.revokePairing(device.id)
            deviceRegistry.updateDeviceStatus(device.id, com.echosystem.localshare.model.DeviceStatus.DISCONNECTED)
            deviceRegistry.updateDevicePairingStatus(device.id, false)
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
