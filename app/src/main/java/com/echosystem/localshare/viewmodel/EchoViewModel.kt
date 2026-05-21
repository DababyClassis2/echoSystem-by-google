package com.echosystem.localshare.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echosystem.localshare.discovery.NsdHelper
import com.echosystem.localshare.model.*
import com.echosystem.localshare.repository.DeviceRegistry
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.service.FileTransferService
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class EchoViewModel @Inject constructor(
    application: Application,
    private val nsdHelper: NsdHelper,
    private val httpClient: HttpClient,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val fileRepository: FileRepository,
    private val deviceRegistry: DeviceRegistry
) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("echosystem_prefs", Context.MODE_PRIVATE)

    // Onboarding
    private val _hasCompletedOnboarding = MutableStateFlow(sharedPrefs.getBoolean("has_onboarding", false))
    val hasCompletedOnboarding = _hasCompletedOnboarding.asStateFlow()

    // App Theme Preference
    private val _themePreference = MutableStateFlow(sharedPrefs.getInt("theme_preference", 2))
    val themePreference = _themePreference.asStateFlow()

    fun setThemePreference(pref: Int) {
        _themePreference.value = pref
        sharedPrefs.edit().putInt("theme_preference", pref).apply()
    }

    // Scanning
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Local identity
    private val _localDeviceName = MutableStateFlow(sharedPrefs.getString("device_name", deviceInfoProvider.name) ?: deviceInfoProvider.name)
    val localDeviceName = _localDeviceName.asStateFlow()

    // Discovered devices list
    val devicesList = deviceRegistry.devices

    // Received files list
    val receivedFiles = fileRepository.receivedFiles

    // Local files for sending
    private val _allFiles = MutableStateFlow<List<LocalFile>>(emptyList())
    val allFiles = _allFiles.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds = _selectedFileIds.asStateFlow()

    // Transfer sessions
    private val _transferSessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferSessions = _transferSessions.asStateFlow()

    // History
    private val _historyRecords = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyRecords = _historyRecords.asStateFlow()

    private val transferJobs = mutableMapOf<String, Job>()

    init {
        // Start foreground service (Ktor Server)
        val intent = Intent(application, FileTransferService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            application.startForegroundService(intent)
        } else {
            application.startService(intent)
        }

        // Register ourselves in NSD
        nsdHelper.registerService(_localDeviceName.value, deviceInfoProvider.port)

        // Observe NSD discovery
        viewModelScope.launch {
            nsdHelper.discoveryFlow.collect { candidate ->
                deviceRegistry.addOrUpdate(Device(
                    id = candidate.ip + ":" + candidate.port,
                    name = candidate.deviceName,
                    ip = candidate.ip,
                    port = candidate.port,
                    protocols = listOf(candidate.protocol),
                    signalStrength = 1.0f
                ))
            }
        }
        
        // Start discovery by default
        toggleScanning()
    }

    fun toggleScanning() {
        if (_isScanning.value) {
            nsdHelper.stopDiscovery()
            _isScanning.value = false
        } else {
            deviceRegistry.clear()
            nsdHelper.startDiscovery()
            _isScanning.value = true
        }
    }

    fun importFiles(uris: List<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        val newFiles = uris.mapNotNull { uri ->
            try {
                var name = "UnknownFile_${System.currentTimeMillis()}"
                var size = 0L
                
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
                
                LocalFile(
                    id = uri.toString(),
                    name = name,
                    sizeBytes = size,
                    category = "Imported",
                    dateAdded = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    uriString = uri.toString()
                )
            } catch (e: Exception) {
                Log.e("EchoVM", "Error importing file", e)
                null
            }
        }
        _allFiles.update { it + newFiles }
    }

    fun toggleFileSelection(fileId: String) {
        _selectedFileIds.update { if (it.contains(fileId)) it - fileId else it + fileId }
    }

    fun initiateSendToDevice(device: Device) {
        val selectedFiles = _allFiles.value.filter { it.id in _selectedFileIds.value }
        if (selectedFiles.isEmpty()) return

        val sessionId = UUID.randomUUID().toString()
        val items = selectedFiles.map { TransferItem(UUID.randomUUID().toString(), it.name, it.sizeBytes) }
        val session = TransferSession(sessionId, device, isSending = true, files = items)
        
        _transferSessions.update { it + session }
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                for (i in selectedFiles.indices) {
                    val file = selectedFiles[i]
                    val item = items[i]
                    
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(Uri.parse(file.uriString))
                        ?: throw Exception("Could not open input stream")
                    
                    val response: HttpResponse = httpClient.post("http://${device.ip}:${device.port}/transfer/upload") {
                        header("File-Name", file.name)
                        setBody(inputStream.readBytes()) // Simple body for now, in real apps use MultiPart or Streaming
                        onUpload { bytesSent, totalBytes ->
                            updateProgress(sessionId, item.id, bytesSent, 0) // Speed calculation omitted for brevity
                        }
                    }
                    
                    if (response.status == HttpStatusCode.OK) {
                        updateProgress(sessionId, item.id, item.sizeBytes, 0)
                    } else {
                        throw Exception("Upload failed with status ${response.status}")
                    }
                }
                markSessionFinished(sessionId, SessionStatus.SUCCESS)
            } catch (e: Exception) {
                Log.e("EchoVM", "Transfer failed", e)
                markSessionFinished(sessionId, SessionStatus.FAILED)
            }
        }
        transferJobs[sessionId] = job
    }

    private fun updateProgress(sessionId: String, itemId: String, bytes: Long, speed: Long) {
        _transferSessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    val updatedFiles = s.files.map { f ->
                        if (f.id == itemId) f.copy(progressBytes = bytes, isCompleted = bytes >= f.sizeBytes) else f
                    }
                    s.copy(files = updatedFiles, currentSpeedBytesPerSecond = speed)
                } else s
            }
        }
    }

    private fun markSessionFinished(sessionId: String, status: SessionStatus) {
        _transferSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(status = status) else it }
        }
        
        val session = _transferSessions.value.find { it.id == sessionId } ?: return
        val record = HistoryRecord(
            id = UUID.randomUUID().toString(),
            deviceName = session.remoteDevice.name,
            isSent = true,
            fileNameSummary = if (session.files.size == 1) session.files[0].name else "${session.files[0].name} (+${session.files.size - 1})",
            totalSize = session.totalBytes,
            timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            isSuccess = status == SessionStatus.SUCCESS,
            speedString = session.speedFormatted,
            durationSeconds = 0
        )
        _historyRecords.update { listOf(record) + it }
    }

    override fun onCleared() {
        nsdHelper.unregisterService()
        nsdHelper.stopDiscovery()
        super.onCleared()
    }
}
