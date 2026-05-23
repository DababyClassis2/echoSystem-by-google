package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.model.NsdState
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
import java.io.File
import javax.inject.Inject

import com.echosystem.localshare.security.TrustManager
import com.echosystem.localshare.core.CoreSystemSupervisor

@HiltViewModel
class EchoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRegistry: DeviceRegistry,
    private val fileRepository: FileRepository,
    val pairingManager: PairingManager,
    val trustManager: TrustManager,
    private val nsdHelper: NsdHelper,
    private val httpClient: HttpClient,
    private val serverEventBus: ServerEventBus,
    val coreSystemSupervisor: CoreSystemSupervisor
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList
    
    val nsdState: StateFlow<NsdState> = nsdHelper.state
    
    // File Browser Logic
    private val _rootDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
    private val _currentDir = MutableStateFlow(_rootDir)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _browserFiles = MutableStateFlow<List<File>>(emptyList())
    val browserFiles: StateFlow<List<File>> = _browserFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles.asStateFlow()

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

    // [V1.0.3] Event Deduplication Logic
    private val handledEventMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

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
        ensureStandardFolders()
        refreshBrowserFiles()

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
            is ServerEvent.PairingRequest -> {
                _incomingPairingRequest.value = event
                AppLogger.logEvent("EchoViewModel", "Received pairing connection query from client ID: ${event.deviceId}")
            }
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
                AppLogger.logEvent("EchoViewModel", "Incoming transmission starting: ${event.fileName} (${event.size} bytes)")
            }
            is ServerEvent.TransferCompleted -> {
                _transferProgress.update { current ->
                    current.map { 
                        if (it.id == event.fileId || it.fileName == event.fileId) it.copy(status = TransferStatus.COMPLETED, progress = 1f) 
                        else it 
                    }
                }
                AppNotificationManager.notifyFileReceived(context, event.fileId, "Local Portal Sync")
                AppLogger.logEvent("EchoViewModel", "Incoming transfer successfully completed: ${event.fileId}")
                loadReceivedFiles() 
            }
            is ServerEvent.TransferFailed -> {
                _transferProgress.update { current ->
                    current.map { if (it.id == event.fileId || it.fileName == event.fileId) it.copy(status = TransferStatus.FAILED) else it }
                }
                AppNotificationManager.notifyTransferFailed(context, "Incoming transfer session failed: ${event.error}")
                AppLogger.logEvent("EchoViewModel", "Incoming transfer failed on ID ${event.fileId}: ${event.error}")
            }
            is ServerEvent.TransferProgress -> {
                // Throttle UI progress updates (max 10 per second per file or significant change)
                _transferProgress.update { current ->
                    current.map {
                        if ((it.id == event.fileId || it.fileName == event.fileId) && Math.abs(it.progress - event.progress) > 0.05f) {
                            it.copy(progress = event.progress)
                        } else it
                    }
                }
            }
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

    fun generatePairingPin() {
        _pairingPin.value = pairingManager.generatePin()
    }

    fun clearIncomingPairing() {
        _incomingPairingRequest.value = null
    }

    fun acceptPairing(event: ServerEvent.PairingRequest) {
        pairingManager.markAsPaired(event.deviceId)
        trustManager.setDeviceTrust(event.deviceId, event.deviceName, true)
        trustManager.setDevicePermissions(event.deviceId, setOf(
            com.echosystem.localshare.model.DevicePermission.BROWSE_FILES,
            com.echosystem.localshare.model.DevicePermission.DOWNLOAD_FILES,
            com.echosystem.localshare.model.DevicePermission.UPLOAD_FILES,
            com.echosystem.localshare.model.DevicePermission.DELETE_FILES
        ))
        deviceRegistry.updateDevicePairingStatus(event.deviceId, true)
        deviceRegistry.updateDeviceStatus(event.deviceId, com.echosystem.localshare.model.DeviceStatus.CONNECTED)
        _incomingPairingRequest.value = null
        AppLogger.logEvent("EchoViewModel", "Accepted pairing request from ${event.deviceName} (${event.deviceId})")
    }

    fun rejectPairing(event: ServerEvent.PairingRequest) {
        pairingManager.revokePairing(event.deviceId)
        deviceRegistry.updateDevicePairingStatus(event.deviceId, false)
        deviceRegistry.updateDeviceStatus(event.deviceId, com.echosystem.localshare.model.DeviceStatus.DISCONNECTED)
        _incomingPairingRequest.value = null
        AppLogger.logEvent("EchoViewModel", "Rejected pairing request from ${event.deviceName}")
    }

    fun blockDeviceFromPairing(event: ServerEvent.PairingRequest) {
        trustManager.setDeviceBlocked(event.deviceId, true)
        pairingManager.revokePairing(event.deviceId)
        deviceRegistry.updateDevicePairingStatus(event.deviceId, false)
        deviceRegistry.updateDeviceStatus(event.deviceId, com.echosystem.localshare.model.DeviceStatus.DISCONNECTED)
        _incomingPairingRequest.value = null
        AppLogger.logEvent("EchoViewModel", "BLOCKED companion from pairing: ${event.deviceName} (${event.deviceId})")
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
                    setBody(com.echosystem.localshare.server.routes.PairingRequest(myDeviceId, pin, pairingManager.getDeviceNodeName()))
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

    // --- File Browser 2.0 Logic ---
    private fun ensureStandardFolders() {
        listOf("Received", "Sent", "Shared").forEach { subDir ->
            val folder = File(_rootDir, subDir)
            if (!folder.exists()) folder.mkdirs()
        }
    }

    fun refreshBrowserFiles() {
        val dir = _currentDir.value
        if (!dir.exists()) dir.mkdirs()
        
        val files = dir.listFiles()?.let { listOf(*it) } ?: emptyList()
        _browserFiles.value = files.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        )
    }

    fun navigateTo(folder: File) {
        if (folder.isDirectory) {
            _currentDir.value = folder
            clearSelection()
            refreshBrowserFiles()
        }
    }

    fun navigateBack(): Boolean {
        val current = _currentDir.value
        if (current == _rootDir) return false
        
        val parent = current.parentFile ?: _rootDir
        _currentDir.value = parent
        clearSelection()
        refreshBrowserFiles()
        return true
    }

    fun toggleSelection(file: File) {
        _selectedFiles.update { current: Set<File> ->
            if (current.contains(file)) current.filter { it != file }.toSet() else current + file
        }
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    fun sendSelectedFilesToDevice(device: Device) {
        val filesToSend = _selectedFiles.value.toList()
        if (filesToSend.isEmpty()) return
        
        val uris = filesToSend.map { Uri.fromFile(it) }
        sendMultipleFilesToDevice(device, uris)
        clearSelection()
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            _selectedFiles.value.forEach { file ->
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }
            clearSelection()
            refreshBrowserFiles()
        }
    }

    fun renameFile(file: File, newName: String) {
        val dest = File(file.parentFile, newName)
        if (file.renameTo(dest)) {
            refreshBrowserFiles()
        }
    }
}
