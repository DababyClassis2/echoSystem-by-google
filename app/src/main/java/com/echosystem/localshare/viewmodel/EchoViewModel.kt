package com.echosystem.localshare.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.*
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
    private val deviceRegistry: DeviceRegistry,
    private val fileRepository: FileRepository,
    private val pairingManager: PairingManager,
    private val nsdHelper: NsdHelper,
    private val httpClient: HttpClient
) : ViewModel() {

    val devices: StateFlow<List<Device>> = deviceRegistry.deviceList

    private val _transferProgress = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferProgress: StateFlow<List<FileTransfer>> = _transferProgress.asStateFlow()

    private val _pairingPin = MutableStateFlow<String?>(null)
    val pairingPin: StateFlow<String?> = _pairingPin.asStateFlow()

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
                        val progressValue = if (contentLength > 0) bytesSentTotal.toFloat() / contentLength else 0f
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

    private fun updateTransferProgress(id: String, progress: Float) {
        _transferProgress.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }

    private fun updateTransferStatus(id: String, status: TransferStatus) {
        _transferProgress.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }
}
