package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EchoViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("echosystem_prefs", Context.MODE_PRIVATE)

    // Onboarding
    private val _hasCompletedOnboarding = MutableStateFlow(sharedPrefs.getBoolean("has_onboarding", false))
    val hasCompletedOnboarding = _hasCompletedOnboarding.asStateFlow()

    // Scanning
    private val _isScanning = MutableStateFlow(true)
    val isScanning = _isScanning.asStateFlow()

    // Protocols
    private val _protocols = MutableStateFlow(
        mapOf(
            Protocol.BLE to true,
            Protocol.NSD to true,
            Protocol.UDP to true,
            Protocol.WIFI_DIRECT to true
        )
    )
    val protocols = _protocols.asStateFlow()

    // Local settings
    private val _localDeviceName = MutableStateFlow(sharedPrefs.getString("device_name", "My Pixel 8 Pro") ?: "My Pixel 8 Pro")
    val localDeviceName = _localDeviceName.asStateFlow()

    private val _autoAccept = MutableStateFlow(sharedPrefs.getBoolean("auto_accept", false))
    val autoAccept = _autoAccept.asStateFlow()

    private val _requirePairing = MutableStateFlow(sharedPrefs.getBoolean("require_pairing", true))
    val requirePairing = _requirePairing.asStateFlow()

    // Devices (Static reference of all possibilities)
    private val allAvailableDevices = listOf(
        Device("d1", "MacBook Pro M3", "192.168.1.101", 8080, false, listOf("nsd"), 0.92f, "macOS Sonoma"),
        Device("d2", "Galaxy S24 Ultra", "192.168.1.42", 8081, false, listOf("ble", "nsd", "udp"), 0.78f, "Android 14 (OneUI)"),
        Device("d3", "iPad Pro 11\"", "192.168.1.53", 8080, false, listOf("nsd"), 0.85f, "iPadOS 17"),
        Device("d4", "Pixel Tablet", "192.168.1.71", 8082, false, listOf("wifi_direct", "udp"), 0.65f, "Android 14"),
        Device("d5", "Sony Bravia 4K TV", "192.168.1.12", 9001, false, listOf("udp"), 0.45f, "Android TV 12")
    )

    // Dynamic paired status state
    private val _pairedDeviceIds = MutableStateFlow(
        sharedPrefs.getStringSet("paired_ids", emptySet()) ?: emptySet()
    )

    // Current discovered devices list in the UI (scanned-in dependently)
    private val _devicesList = MutableStateFlow<List<Device>>(emptyList())
    val devicesList = _devicesList.asStateFlow()

    // Mock Files available in browser
    private val _allFiles = MutableStateFlow(
        listOf(
            LocalFile("f1", "Annual_Report_2026.pdf", 12976128, "Documents", "Today", "PDF Document"),
            LocalFile("f2", "Pitch_Deck_EchoSystem.pptx", 29779200, "Documents", "Today", "Powerpoint Presentation"),
            LocalFile("f3", "Invoice_May_782.pdf", 1258291, "Documents", "Yesterday", "Receipt & Statement"),
            LocalFile("f4", "IMG_Sunset_Tahoe.jpg", 4325376, "Images", "Today", "4032 x 3024 • JPEG"),
            LocalFile("f5", "LocalShare_Wireframes.png", 9122611, "Images", "Yesterday", "PNG Image Asset"),
            LocalFile("f6", "Team_Launch_Photo.jpg", 3701504, "Images", "2 days ago", "Camera Capture"),
            LocalFile("f7", "VLOG_Paris_Cafes.mp4", 155353152, "Videos", "Today", "HD 1080p • 24fps"),
            LocalFile("f8", "Review_AppBuild.mp4", 86095360, "Videos", "Yesterday", "Screen Recording"),
            LocalFile("f9", "Tech_Podcast_Intro.mp3", 47395648, "Audio", "3 days ago", "Stereo Output • 320kbps"),
            LocalFile("f10", "Zen_Meditation_Loop.wav", 67633152, "Audio", "5 days ago", "Ambient Synth Audio")
        )
    )
    val allFiles = _allFiles.asStateFlow()

    // Selected files
    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds = _selectedFileIds.asStateFlow()

    // Pairing handshake dialog state
    data class PairingState(
        val targetDevice: Device,
        val pin: String,
        val secondsRemaining: Int,
        val progressFraction: Float
    )
    private val _activePairing = MutableStateFlow<PairingState?>(null)
    val activePairing = _activePairing.asStateFlow()

    // Transfer list
    private val _transferSessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val transferSessions = _transferSessions.asStateFlow()

    // History
    private val _historyRecords = MutableStateFlow<List<HistoryRecord>>(emptyList())
    val historyRecords = _historyRecords.asStateFlow()

    // Active background scan jobs
    private var scanJob: Job? = null
    private var pairingJob: Job? = null
    private var transferJobs = mutableMapOf<String, Job>()

    init {
        startScanningSimulation()
    }

    fun completeOnboarding() {
        _hasCompletedOnboarding.value = true
        sharedPrefs.edit().putBoolean("has_onboarding", true).apply()
    }

    fun resetOnboarding() {
        _hasCompletedOnboarding.value = false
        sharedPrefs.edit().putBoolean("has_onboarding", false).apply()
    }

    fun setLocalDeviceName(name: String) {
        _localDeviceName.value = name
        sharedPrefs.edit().putString("device_name", name).apply()
    }

    fun setAutoAccept(auto: Boolean) {
        _autoAccept.value = auto
        sharedPrefs.edit().putBoolean("auto_accept", auto).apply()
    }

    fun setRequirePairing(req: Boolean) {
        _requirePairing.value = req
        sharedPrefs.edit().putBoolean("require_pairing", req).apply()
    }

    fun toggleProtocol(protocol: Protocol) {
        _protocols.update { current ->
            val updated = current.toMutableMap()
            updated[protocol] = !(current[protocol] ?: true)
            updated
        }
        // Force refresh devices based on active protocols
        filterAndPopulateDevices()
    }

    fun toggleFileSelection(fileId: String) {
        _selectedFileIds.update { current ->
            if (current.contains(fileId)) current - fileId else current + fileId
        }
    }

    fun clearSelections() {
        _selectedFileIds.value = emptySet()
    }

    fun toggleScanning() {
        val nextState = !_isScanning.value
        _isScanning.value = nextState
        if (nextState) {
            startScanningSimulation()
        } else {
            scanJob?.cancel()
            _devicesList.value = emptyList()
        }
    }

    fun forceScanRefresh() {
        _devicesList.value = emptyList()
        startScanningSimulation()
    }

    private fun startScanningSimulation() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            // Periodically add devices that match the enabled protocols
            _devicesList.value = emptyList()
            var index = 0
            while (index < allAvailableDevices.size) {
                delay(1200 + (1000..2500).random().toLong())
                if (!_isScanning.value) break
                index++
                filterAndPopulateDevices(index)
            }
        }
    }

    private fun filterAndPopulateDevices(limit: Int = allAvailableDevices.size) {
        val activeProtocolsList = _protocols.value.filterValues { it }.keys.map { it.id }
        val pairedIds = _pairedDeviceIds.value

        val filtered = allAvailableDevices.take(limit).filter { candidate ->
            // Match if candidate device shares at least one active protocol
            candidate.protocols.any { it in activeProtocolsList }
        }.map { device ->
            // Decorate with paired flag
            device.copy(isPaired = device.id in pairedIds)
        }
        _devicesList.value = filtered
    }

    // Connect & Start sharing
    fun initiateSendToDevice(device: Device) {
        val selectedFiles = _allFiles.value.filter { it.id in _selectedFileIds.value }
        if (selectedFiles.isEmpty()) return

        // Check if pairing is required and not yet paired
        val isPaired = device.id in _pairedDeviceIds.value
        if (_requirePairing.value && !isPaired) {
            startPairingHandshake(device, selectedFiles)
        } else {
            startFileTransferSession(device, selectedFiles, isSending = true)
        }
    }

    private fun startPairingHandshake(device: Device, filesToSend: List<LocalFile>) {
        pairingJob?.cancel()
        val randomPin = (100..999).random().toString() + "" + (100..999).random().toString()
        _activePairing.value = PairingState(device, randomPin, 30, 1.0f)

        pairingJob = viewModelScope.launch {
            var remaining = 30
            while (remaining > 0) {
                delay(1000)
                remaining--
                _activePairing.update { current ->
                    current?.copy(
                        secondsRemaining = remaining,
                        progressFraction = remaining.toFloat() / 30f
                    )
                }
            }
            // Expired
            _activePairing.value = null
        }
    }

    fun confirmCredentialsAndPair() {
        val currentPairing = _activePairing.value ?: return
        val device = currentPairing.targetDevice
        
        pairingJob?.cancel()
        _activePairing.value = null

        // Save pair status
        val updatedSet = _pairedDeviceIds.value + device.id
        _pairedDeviceIds.value = updatedSet
        sharedPrefs.edit().putStringSet("paired_ids", updatedSet).apply()

        // Update current device items
        _devicesList.update { list ->
            list.map { if (it.id == device.id) it.copy(isPaired = true) else it }
        }

        // Now trigger the pending file transfer
        val selectedFiles = _allFiles.value.filter { it.id in _selectedFileIds.value }
        if (selectedFiles.isNotEmpty()) {
            startFileTransferSession(device, selectedFiles, isSending = true)
        }
    }

    fun declinePairing() {
        pairingJob?.cancel()
        _activePairing.value = null
    }

    fun unpairDevice(deviceId: String) {
        val updatedSet = _pairedDeviceIds.value - deviceId
        _pairedDeviceIds.value = updatedSet
        sharedPrefs.edit().putStringSet("paired_ids", updatedSet).apply()
        _devicesList.update { list ->
            list.map { if (it.id == deviceId) it.copy(isPaired = false) else it }
        }
    }

    // Trigger dummy receiving file to simulate inbound requests beautifully
    fun simulateInboundTransferRandom() {
        viewModelScope.launch {
            val sender = allAvailableDevices.random()
            val incomingFiles = listOf(
                LocalFile("in_f1", "IMG_Party_Share.jpg", 3450322, "Images", "Today"),
                LocalFile("in_f2", "EchoSystem_Guide.pdf", 5242880, "Documents", "Today")
            )
            // Just simulate acceptance directly or pairing if required
            val isPaired = sender.id in _pairedDeviceIds.value
            if (_requirePairing.value && !isPaired) {
                // Instantly pop a pairing handshake
                startPairingHandshake(sender, emptyList())
            } else {
                startFileTransferSession(sender, incomingFiles, isSending = false)
            }
        }
    }

    private fun startFileTransferSession(device: Device, files: List<LocalFile>, isSending: Boolean) {
        val sessionId = UUID.randomUUID().toString()
        val items = files.map { file ->
            TransferItem(UUID.randomUUID().toString(), file.name, file.sizeBytes)
        }
        val session = TransferSession(sessionId, device, isSending, items)

        _transferSessions.update { it + session }
        clearSelections() // Reset chosen files

        // Process this session in a simulated coroutine Job
        val job = viewModelScope.launch {
            var speedBytes = (1024 * 1024 * 1.5).toLong() // Starting at 1.5MB/s
            var elapsedSeconds = 0

            // Loop to feed bytes
            while (true) {
                delay(500) // update twice per second
                var hasUnfinished = false
                elapsedSeconds++

                // Adjust speed dynamically a bit
                val drift = (-900_000..1_100_000).random()
                speedBytes = (speedBytes + drift).coerceIn((1024 * 1024 * 1.0).toLong(), (1024 * 1024 * 4.8).toLong())

                _transferSessions.update { currentSessions ->
                    currentSessions.map { s ->
                        if (s.id == sessionId) {
                            if (s.status == SessionStatus.PAUSED) {
                                s.currentSpeedBytesPerSecond = 0
                                return@map s
                            }

                            val filesCopy = s.files.map { item ->
                                if (item.isCompleted) return@map item
                                hasUnfinished = true

                                // Give this item some bytes
                                val increment = speedBytes / 2 // since we tick every 500ms
                                val nextProgress = (item.progressBytes + increment).coerceAtMost(item.sizeBytes)
                                
                                item.copy(
                                    progressBytes = nextProgress,
                                    isCompleted = nextProgress == item.sizeBytes
                                )
                            }

                            val isSuccessDone = filesCopy.all { it.isCompleted }
                            s.copy(
                                files = filesCopy,
                                currentSpeedBytesPerSecond = if (isSuccessDone) 0 else speedBytes,
                                secondsElapsed = s.secondsElapsed + 1,
                                status = if (isSuccessDone) SessionStatus.SUCCESS else SessionStatus.ONGOING
                            )
                        } else {
                            s
                        }
                    }
                }

                // Check termination conditions
                val currentSession = _transferSessions.value.firstOrNull { it.id == sessionId }
                if (currentSession == null || currentSession.status == SessionStatus.SUCCESS || currentSession.status == SessionStatus.FAILED) {
                    break
                }
            }

            // Session completed, log into history
            val finSession = _transferSessions.value.firstOrNull { it.id == sessionId }
            if (finSession != null) {
                val sumBytes = finSession.totalBytes
                val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeStr = formatter.format(Date())

                val rec = HistoryRecord(
                    id = UUID.randomUUID().toString(),
                    deviceName = finSession.remoteDevice.name,
                    isSent = finSession.isSending,
                    fileNameSummary = if (finSession.files.size == 1) finSession.files[0].name else "${finSession.files[0].name} (+${finSession.files.size - 1} more)",
                    totalSize = sumBytes,
                    timeString = timeStr,
                    isSuccess = finSession.status == SessionStatus.SUCCESS,
                    speedString = formatSize(speedBytes) + "/s",
                    durationSeconds = elapsedSeconds
                )
                _historyRecords.update { listOf(rec) + it }
            }
        }

        transferJobs[sessionId] = job
    }

    fun pauseSession(sessionId: String) {
        _transferSessions.update { list ->
            list.map {
                if (it.id == sessionId) it.copy(status = SessionStatus.PAUSED, currentSpeedBytesPerSecond = 0) else it
            }
        }
    }

    fun resumeSession(sessionId: String) {
        _transferSessions.update { list ->
            list.map {
                if (it.id == sessionId) it.copy(status = SessionStatus.ONGOING) else it
            }
        }
    }

    fun cancelSession(sessionId: String) {
        transferJobs[sessionId]?.cancel()
        transferJobs.remove(sessionId)

        // Find session to log failure
        val s = _transferSessions.value.firstOrNull { it.id == sessionId }
        if (s != null) {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeStr = formatter.format(Date())
            val rec = HistoryRecord(
                id = UUID.randomUUID().toString(),
                deviceName = s.remoteDevice.name,
                isSent = s.isSending,
                fileNameSummary = if (s.files.size == 1) s.files[0].name else "${s.files[0].name} (+${s.files.size - 1} more)",
                totalSize = s.totalBytes,
                timeString = timeStr,
                isSuccess = false,
                speedString = "0 B/s",
                durationSeconds = s.secondsElapsed
            )
            _historyRecords.update { listOf(rec) + it }
        }

        _transferSessions.update { list ->
            list.filter { it.id != sessionId }
        }
    }

    fun clearHistory() {
        _historyRecords.value = emptyList()
    }

    override fun onCleared() {
        scanJob?.cancel()
        pairingJob?.cancel()
        transferJobs.values.forEach { it.cancel() }
        super.onCleared()
    }
}
