package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.model.TransferStatus
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

@HiltViewModel
class EchoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRegistry: DeviceRegistry,
    private val fileRepository: FileRepository,
    private val pairingManager: PairingManager,
    private val nsdHelper: NsdHelper,
    private val httpClient: HttpClient,
    private val serverEventBus: ServerEventBus
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList

    private val _transferProgress = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferProgress: StateFlow<List<FileTransfer>> = _transferProgress.asStateFlow()

    private val _pairingPin = MutableStateFlow<String?>(null)
    val pairingPin: StateFlow<String?> = _pairingPin.asStateFlow()

    private val _ipAddress = MutableStateFlow(pairingManager.getLocalIp())
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    init {
        // Automatically generate a default PIN and auto-start network discovery
        generatePairingPin()
        startDiscovery()
        loadReceivedFiles()

        // Synchronize with server events for real-time transfers (e.g. from nearby hosts)
        viewModelScope.launch {
            serverEventBus.events.collect { event ->
                when (event) {
                    is ServerEvent.PairingRequest -> {
                        // Securely paired or requested
                    }
                    is ServerEvent.TransferStarted -> {
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
                    }
                    is ServerEvent.TransferFailed -> {
                        updateTransferStatus(event.fileId, TransferStatus.FAILED)
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

            try {
                val inputStream = fileRepository.openInputStream(uri) ?: return@launch
                val response: HttpResponse = httpClient.post("http://${device.ip}:${device.port}/transfer/upload") {
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

                if (response.status == HttpStatusCode.OK) {
                    updateTransferStatus(transfer.id, TransferStatus.COMPLETED)
                } else {
                    updateTransferStatus(transfer.id, TransferStatus.FAILED)
                }
            } catch (e: Exception) {
                updateTransferStatus(transfer.id, TransferStatus.FAILED)
            }
        }
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
                val transfers = files.map { file ->
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
                _transferProgress.value = transfers
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
