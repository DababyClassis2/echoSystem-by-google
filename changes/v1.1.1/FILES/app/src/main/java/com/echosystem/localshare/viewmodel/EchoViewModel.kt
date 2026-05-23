package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.core.CoreSystemSupervisor
import com.echosystem.localshare.core.connection.ConnectionManager
import com.echosystem.localshare.core.connection.ConnectionState
import com.echosystem.localshare.core.TransferEngineCore
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.model.*
import com.echosystem.localshare.notification.AppNotificationManager
import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.security.TrustManager
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
import java.io.File
import javax.inject.Inject

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
    private val connectionManager: ConnectionManager,
    private val transferEngine: TransferEngineCore,
    val coreSystemSupervisor: CoreSystemSupervisor
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList
    val nsdState: StateFlow<NsdState> = nsdHelper.state
    
    // Unified Connection State
    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    // File Browser Logic
    private val _rootDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
    private val _currentDir = MutableStateFlow(_rootDir)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    private val _browserFiles = MutableStateFlow<List<File>>(emptyList())
    val browserFiles: StateFlow<List<File>> = _browserFiles.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<File>>(emptySet())
    val selectedFiles: StateFlow<Set<File>> = _selectedFiles.asStateFlow()

    // Sync with refined Transfer Engine
    val transferQueue: StateFlow<List<FileTransfer>> = transferEngine.transferQueue
    val transferProgress: StateFlow<Map<String, TransferProgress>> = transferEngine.transferProgress

    private val _pairingPin = MutableStateFlow<String?>(null)
    val pairingPin: StateFlow<String?> = _pairingPin.asStateFlow()

    private val _incomingPairingRequest = MutableStateFlow<ServerEvent.PairingRequest?>(null)
    val incomingPairingRequest = _incomingPairingRequest.asStateFlow()

    init {
        generatePairingPin()
        startDiscovery()
        ensureStandardFolders()
        refreshBrowserFiles()

        // Sync legacy events with new engine (Internal adaptation)
        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                processServerEvent(event)
            }
        }
    }

    private fun processServerEvent(event: ServerEvent) {
        when (event) {
            is ServerEvent.PairingRequest -> _incomingPairingRequest.value = event
            is ServerEvent.TransferStarted -> {
                // In-bound transfers also managed through engine in refined version
            }
            // Other events handled by the TransferEngineCore directly in the refined architecture
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

    fun sendFileToDevice(device: Device, uri: Uri) {
        val fileName = fileRepository.getFileName(uri) ?: "unknown_file"
        val fileSize = fileRepository.getFileSize(uri)
        
        val transfer = FileTransfer(
            id = System.currentTimeMillis().toString(),
            fileName = fileName,
            size = fileSize,
            status = TransferStatus.QUEUED,
            isIncoming = false,
            remoteDeviceName = device.name
        )
        
        transferEngine.enqueueTransfer(transfer, uri) { task, checksum, updateProgress ->
            val inputStream = fileRepository.openInputStream(uri) ?: return@enqueueTransfer
            
            coreSystemSupervisor.runWithAutoRetry(device) { resolvedIp ->
                httpClient.post("http://$resolvedIp:${device.port}/transfer/upload") {
                    header("X-Checksum", checksum)
                    header("X-Device-Id", pairingManager.getLocalIp())
                    setBody(MultiPartFormDataContent(
                        formData {
                            append("file", Input {
                                // Enforcing large chunk streaming protocol
                                val buffer = ByteArray(256 * 1024)
                                inputStream.use { input ->
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        writeFully(buffer, 0, read)
                                    }
                                }
                            }, Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            })
                        }
                    ))
                    onUpload { bytesSentTotal, contentLength ->
                        val progress = if (contentLength != null && contentLength > 0) bytesSentTotal.toFloat() / contentLength else 0f
                        updateProgress(progress, bytesSentTotal) // Engine tracks deltas or we pass total if we change signature
                    }
                }
            }
        }
    }

    // Legacy support methods redirected to unified manager
    fun switchNetworkMode(mode: com.echosystem.localshare.core.NetworkMode) {
        connectionManager.setNetworkMode(mode)
    }

    // Existing File Browser & Management methods...
    fun refreshBrowserFiles() {
        val dir = _currentDir.value
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles()?.toList() ?: emptyList()
        _browserFiles.value = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    private fun ensureStandardFolders() {
        listOf("Received", "Sent", "Shared").forEach { File(_rootDir, it).mkdirs() }
    }

    fun navigateTo(folder: File) {
        if (folder.isDirectory) {
            _currentDir.value = folder
            refreshBrowserFiles()
        }
    }

    fun toggleSelection(file: File) {
        _selectedFiles.update { if (it.contains(file)) it - file else it + file }
    }

    fun clearSelection() { _selectedFiles.value = emptySet() }
}
