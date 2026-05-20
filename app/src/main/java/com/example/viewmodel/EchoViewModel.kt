package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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

    // Devices (Static reference of all possibilities for simulation)
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

    // Current discovered devices list in the UI (scanned-in dynamically)
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

    // REAL NETWORK VARIABLES & STACK
    private var tcpServerSocket: ServerSocket? = null
    private var activeServerPort = 8080
    private val localUniqueId = UUID.randomUUID().toString().take(6)
    private val realDiscoveredDevices = mutableMapOf<String, Device>()

    // Core network networking coroutine jobs
    private var tcpServerJob: Job? = null
    private var udpReceiveJob: Job? = null
    private var udpSendJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    init {
        startRealNetworkServices()
        startScanningSimulation()
    }

    // ==========================================
    // REAL NETWORK SERVICE DISCOVERY AND SOCKETS
    // ==========================================
    private fun startRealNetworkServices() {
        // 1. Fire up background TCP Socket Server for incoming files
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val server = ServerSocket(0) // Allocate dynamic free port automatically
                tcpServerSocket = server
                activeServerPort = server.localPort
                android.util.Log.d("EchoNetwork", "TCP Server successfully listening on port $activeServerPort")
                
                // Spawn acceptance processing loop
                runTcpServerAcceptLoop(server)
            } catch (e: Exception) {
                android.util.Log.e("EchoNetwork", "Failed to start TCP Server Socket: ${e.message}")
            }
        }

        // 2. Start Wireless UDP Peer discovery broadcaster and receiver
        startUdpDiscovery()

        // 3. Start local Network Service Discovery (mDNS) registration & scanner
        startNsdServices()
    }

    private fun runTcpServerAcceptLoop(server: ServerSocket) {
        tcpServerJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                try {
                    val socket = server.accept() ?: break
                    android.util.Log.d("EchoNetwork", "Inbound socket connection from target ${socket.inetAddress}")
                    handleIncomingConnection(socket)
                } catch (e: Exception) {
                    android.util.Log.d("EchoNetwork", "Server socket terminated or closed: ${e.message}")
                    break
                }
            }
        }
    }

    private fun handleIncomingConnection(socket: Socket) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val inputStream = socket.getInputStream()
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                
                val headerLine = reader.readLine()
                if (headerLine != null && headerLine.startsWith("ECHO_FILE_HEADER|")) {
                    // Header Protocol format: ECHO_FILE_HEADER|sessionId|senderDeviceName|fileListCsv
                    val parts = headerLine.split("|")
                    if (parts.size >= 4) {
                        val sessionId = parts[1]
                        val senderName = parts[2]
                        val fileListCsv = parts[3]
                        
                        val remoteIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
                        
                        // Treat as professional Real Device
                        val senderDevice = Device(
                            id = "real_$remoteIp",
                            name = senderName,
                            ip = remoteIp,
                            port = socket.port,
                            protocols = listOf("udp", "nsd"),
                            signalStrength = 0.95f,
                            osName = "Active Network Node"
                        )
                        
                        // Parse files csv
                        val transferItems = fileListCsv.split(",").filter { it.contains(":") }.map { fileToken ->
                            val tokenParts = fileToken.split(":")
                            val fileName = tokenParts[0]
                            val fileSize = tokenParts[1].toLongOrNull() ?: 1024L
                            TransferItem(
                                id = UUID.randomUUID().toString(),
                                name = fileName,
                                sizeBytes = fileSize
                            )
                        }
                        
                        if (transferItems.isNotEmpty()) {
                            val session = TransferSession(
                                id = sessionId,
                                remoteDevice = senderDevice,
                                isSending = false,
                                files = transferItems,
                                status = SessionStatus.ONGOING,
                                currentSpeedBytesPerSecond = 0
                            )
                            
                            _transferSessions.update { it + session }
                            
                            // Read stream in chunks to represent progress
                            val buffer = ByteArray(1024 * 32)
                            var lastUpdateTime = System.currentTimeMillis()
                            var bytesInPeriod = 0L
                            
                            for (item in transferItems) {
                                var itemBytesRead = 0L
                                while (itemBytesRead < item.sizeBytes) {
                                    val toRead = minOf(item.sizeBytes - itemBytesRead, buffer.size.toLong()).toInt()
                                    val read = inputStream.read(buffer, 0, toRead)
                                    if (read == -1) break
                                    itemBytesRead += read
                                    bytesInPeriod += read
                                    
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateTime >= 300) {
                                        val timeDiffSecs = (now - lastUpdateTime) / 1000.0
                                        val calculatedSpeed = if (timeDiffSecs > 0) (bytesInPeriod / timeDiffSecs).toLong() else 0L
                                        
                                        updateReceivingProgress(sessionId, item.id, itemBytesRead, calculatedSpeed)
                                        lastUpdateTime = now
                                        bytesInPeriod = 0
                                    }
                                }
                                updateReceivingProgress(sessionId, item.id, item.sizeBytes, 0L)
                            }
                            markSessionFinished(sessionId, SessionStatus.SUCCESS)
                        }
                    }
                }
                socket.close()
            } catch (e: Exception) {
                android.util.Log.e("EchoNetwork", "Error processing incoming data: ${e.message}")
            }
        }
    }

    private fun startRealFileTransferSession(device: Device, files: List<LocalFile>) {
        val sessionId = UUID.randomUUID().toString()
        val items = files.map { file ->
            TransferItem(UUID.randomUUID().toString(), file.name, file.sizeBytes)
        }
        val session = TransferSession(sessionId, device, isSending = true, files = items)
        
        _transferSessions.update { it + session }
        clearSelections()
        
        val job = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var socket: Socket? = null
            try {
                android.util.Log.d("EchoNetwork", "Connecting real client stream to target ${device.ip}:${device.port}")
                socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress(device.ip, device.port), 5500)
                
                val outputStream = socket.getOutputStream()
                val writer = java.io.BufferedWriter(java.io.OutputStreamWriter(outputStream))
                
                // Handshake payload structure: ECHO_FILE_HEADER|sessionId|senderDeviceName|fileListCsv
                val fileCsv = files.joinToString(",") { "${it.name}:${it.sizeBytes}" }
                writer.write("ECHO_FILE_HEADER|$sessionId|${_localDeviceName.value}|$fileCsv\n")
                writer.flush()
                
                // Stream actual simulated payloads
                val buf = ByteArray(1024 * 32)
                var lastUpdateTime = System.currentTimeMillis()
                var bytesInPeriod = 0L
                
                for (item in items) {
                    var fileBytesSent = 0L
                    while (fileBytesSent < item.sizeBytes) {
                        val toWrite = minOf(item.sizeBytes - fileBytesSent, buf.size.toLong()).toInt()
                        outputStream.write(buf, 0, toWrite)
                        fileBytesSent += toWrite
                        bytesInPeriod += toWrite
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 300) {
                            val timeDiffSecs = (now - lastUpdateTime) / 1000.0
                            val calculatedSpeed = if (timeDiffSecs > 0) (bytesInPeriod / timeDiffSecs).toLong() else 0L
                            
                            updateSendingProgress(sessionId, item.id, fileBytesSent, calculatedSpeed)
                            lastUpdateTime = now
                            bytesInPeriod = 0
                        }
                        delay(2) // keep UI/emulator interactive
                    }
                    updateSendingProgress(sessionId, item.id, item.sizeBytes, 0L)
                }
                outputStream.flush()
                markSessionFinished(sessionId, SessionStatus.SUCCESS)
            } catch (e: Exception) {
                android.util.Log.e("EchoNetwork", "Real connection stream failed: ${e.message}")
                markSessionFinished(sessionId, SessionStatus.FAILED)
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {}
            }
        }
        transferJobs[sessionId] = job
    }

    private fun startUdpDiscovery() {
        udpReceiveJob?.cancel()
        udpReceiveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(8899))
                }
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    val text = String(packet.data, 0, packet.length, java.nio.charset.StandardCharsets.UTF_8).trim()
                    if (text.startsWith("ECHO_PING|")) {
                        val parts = text.split("|")
                        if (parts.size >= 5) {
                            val uid = parts[1]
                            val name = parts[2]
                            val ip = parts[3]
                            val port = parts[4].toIntOrNull() ?: 8080
                            
                            // Filter out loopback discovering oneself
                            if (uid != localUniqueId) {
                                val senderIp = packet.address?.hostAddress ?: ip
                                val device = Device(
                                    id = "real_${uid}",
                                    name = name,
                                    ip = senderIp,
                                    port = port,
                                    protocols = listOf("udp", "nsd"),
                                    signalStrength = 0.94f,
                                    osName = "UDP Broadcast Node"
                                )
                                viewModelScope.launch {
                                    realDiscoveredDevices[uid] = device
                                    filterAndPopulateDevices()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EchoNetwork", "UDP receiver exception: ${e.message}")
            } finally {
                socket?.close()
            }
        }

        udpSendJob?.cancel()
        udpSendJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                
                while (true) {
                    if (_isScanning.value && (_protocols.value[Protocol.UDP] == true)) {
                        val payload = "ECHO_PING|${localUniqueId}|${_localDeviceName.value}|${getLocalIpAddress()}|${activeServerPort}"
                        val bytes = payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                        
                        // Send broadcast packet to subnet
                        val packet = DatagramPacket(
                            bytes,
                            bytes.size,
                            InetAddress.getByName("255.255.255.255"),
                            8899
                        )
                        socket.send(packet)
                        
                        // Local loopback packets for quick mock validation on same client emulators
                        val backPacket = DatagramPacket(
                            bytes,
                            bytes.size,
                            InetAddress.getByName("127.0.0.1"),
                            8899
                        )
                        socket.send(backPacket)
                    }
                    delay(3000)
                }
            } catch (e: Exception) {
                android.util.Log.e("EchoNetwork", "UDP transmitter exception: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun startNsdServices() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager

        // Register our local service with mDNS/NSD service type
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Echo_${_localDeviceName.value}_${localUniqueId}"
            serviceType = "_echosystem._tcp"
            setPort(activeServerPort)
        }

        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registeredServiceInfo: NsdServiceInfo) {
                android.util.Log.d("EchoNSD", "Successfully Registered: ${registeredServiceInfo.serviceName}")
            }

            override fun onRegistrationFailed(failedServiceInfo: NsdServiceInfo, errorCode: Int) {
                android.util.Log.e("EchoNSD", "Failed to Register: $errorCode")
            }

            override fun onServiceUnregistered(unregisteredServiceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(unregisteredServiceInfo: NsdServiceInfo, errorCode: Int) {}
        }

        try {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener)
        } catch (e: Exception) {
            android.util.Log.e("EchoNSD", "NSD register error: ${e.message}")
        }

        startNsdDiscovery()
    }

    private fun startNsdDiscovery() {
        val manager = nsdManager ?: return
        
        nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                android.util.Log.e("EchoNSD", "Discovery failed: $errorCode")
                try {
                    manager.stopServiceDiscovery(this)
                } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onDiscoveryStarted(serviceType: String) {
                android.util.Log.d("EchoNSD", "NSD scan active")
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == "_echosystem._tcp" || serviceInfo.serviceType.startsWith("_echosystem")) {
                    if (serviceInfo.serviceName.contains(localUniqueId)) {
                        return
                    }
                    
                    try {
                        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(resolveServiceInfo: NsdServiceInfo, errorCode: Int) {}

                            override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                                val rawName = resolvedServiceInfo.serviceName
                                val elegantName = rawName.substringBeforeLast("_").substringAfter("Echo_")
                                val suffixId = rawName.substringAfterLast("_")
                                
                                val peerIp = resolvedServiceInfo.host?.hostAddress ?: "127.0.0.1"
                                val peerPort = resolvedServiceInfo.port
                                
                                val device = Device(
                                    id = "real_${suffixId}",
                                    name = elegantName,
                                    ip = peerIp,
                                    port = peerPort,
                                    protocols = listOf("nsd", "udp"),
                                    signalStrength = 0.99f,
                                    osName = "mDNS / NSD Node"
                                )
                                
                                viewModelScope.launch {
                                    realDiscoveredDevices[suffixId] = device
                                    filterAndPopulateDevices()
                                }
                            }
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName
                val suffixId = serviceName.substringAfterLast("_")
                viewModelScope.launch {
                    realDiscoveredDevices.remove(suffixId)
                    filterAndPopulateDevices()
                }
            }
        }

        try {
            manager.discoverServices("_echosystem._tcp", NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) {
            android.util.Log.e("EchoNSD", "NSD scan error: ${e.message}")
        }
    }

    private fun stopNsdDiscoveryAndRegistration() {
        val manager = nsdManager ?: return
        try {
            val reg = nsdRegistrationListener
            if (reg != null) {
                manager.unregisterService(reg)
            }
        } catch (ignored: Exception) {}
        try {
            val disc = nsdDiscoveryListener
            if (disc != null) {
                manager.stopServiceDiscovery(disc)
            }
        } catch (ignored: Exception) {}
        nsdRegistrationListener = null
        nsdDiscoveryListener = null
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun updateReceivingProgress(sessionId: String, itemId: String, progress: Long, speed: Long) {
        _transferSessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    val updatedFiles = s.files.map { f ->
                        if (f.id == itemId) {
                            f.copy(progressBytes = progress, isCompleted = progress >= f.sizeBytes)
                        } else {
                            f
                        }
                    }
                    s.copy(
                        files = updatedFiles,
                        currentSpeedBytesPerSecond = speed,
                        secondsElapsed = s.secondsElapsed + 1
                    )
                } else {
                    s
                }
            }
        }
    }

    private fun updateSendingProgress(sessionId: String, itemId: String, progress: Long, speed: Long) {
        _transferSessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    val updatedFiles = s.files.map { f ->
                        if (f.id == itemId) {
                            f.copy(progressBytes = progress, isCompleted = progress >= f.sizeBytes)
                        } else {
                            f
                        }
                    }
                    s.copy(
                        files = updatedFiles,
                        currentSpeedBytesPerSecond = speed,
                        secondsElapsed = s.secondsElapsed + 1
                    )
                } else {
                    s
                }
            }
        }
    }

    private fun markSessionFinished(sessionId: String, finalStatus: SessionStatus) {
        _transferSessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    s.copy(
                        status = finalStatus,
                        currentSpeedBytesPerSecond = 0
                    )
                } else {
                    s
                }
            }
        }
        
        // Log to history
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
                isSuccess = finalStatus == SessionStatus.SUCCESS,
                speedString = s.speedFormatted,
                durationSeconds = s.secondsElapsed
            )
            _historyRecords.update { listOf(rec) + it }
        }
    }

    // ==========================================
    // OTHER STANDARD VIEWMODEL METHODS
    // ==========================================

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
        
        // Re-register to announce named changes
        stopNsdDiscoveryAndRegistration()
        startNsdServices()
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
            startUdpDiscovery()
            startNsdDiscovery()
        } else {
            scanJob?.cancel()
            stopNsdDiscoveryAndRegistration()
            udpReceiveJob?.cancel()
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

        val filteredSimulated = allAvailableDevices.take(limit).filter { candidate ->
            candidate.protocols.any { it in activeProtocolsList }
        }.map { device ->
            device.copy(isPaired = device.id in pairedIds)
        }

        val filteredReal = realDiscoveredDevices.values.filter { candidate ->
            candidate.protocols.any { it in activeProtocolsList }
        }.map { device ->
            device.copy(isPaired = device.id in pairedIds)
        }

        _devicesList.value = filteredReal + filteredSimulated
    }

    fun initiateSendToDevice(device: Device) {
        val selectedFiles = _allFiles.value.filter { it.id in _selectedFileIds.value }
        if (selectedFiles.isEmpty()) return

        val isPaired = device.id in _pairedDeviceIds.value
        if (_requirePairing.value && !isPaired) {
            startPairingHandshake(device, selectedFiles)
        } else {
            if (device.id.startsWith("real_")) {
                startRealFileTransferSession(device, selectedFiles)
            } else {
                startFileTransferSession(device, selectedFiles, isSending = true)
            }
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
            _activePairing.value = null
        }
    }

    fun confirmCredentialsAndPair() {
        val currentPairing = _activePairing.value ?: return
        val device = currentPairing.targetDevice
        
        pairingJob?.cancel()
        _activePairing.value = null

        val updatedSet = _pairedDeviceIds.value + device.id
        _pairedDeviceIds.value = updatedSet
        sharedPrefs.edit().putStringSet("paired_ids", updatedSet).apply()

        _devicesList.update { list ->
            list.map { if (it.id == device.id) it.copy(isPaired = true) else it }
        }

        val selectedFiles = _allFiles.value.filter { it.id in _selectedFileIds.value }
        if (selectedFiles.isNotEmpty()) {
            if (device.id.startsWith("real_")) {
                startRealFileTransferSession(device, selectedFiles)
            } else {
                startFileTransferSession(device, selectedFiles, isSending = true)
            }
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

    fun simulateInboundTransferRandom() {
        viewModelScope.launch {
            val sender = allAvailableDevices.random()
            val incomingFiles = listOf(
                LocalFile("in_f1", "IMG_Party_Share.jpg", 3450322, "Images", "Today"),
                LocalFile("in_f2", "EchoSystem_Guide.pdf", 5242880, "Documents", "Today")
            )
            val isPaired = sender.id in _pairedDeviceIds.value
            if (_requirePairing.value && !isPaired) {
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
        clearSelections()

        val job = viewModelScope.launch {
            var speedBytes = (1024 * 1024 * 1.5).toLong()
            var elapsedSeconds = 0

            while (true) {
                delay(500)
                var hasUnfinished = false
                elapsedSeconds++

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

                                val increment = speedBytes / 2
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

                val currentSession = _transferSessions.value.firstOrNull { it.id == sessionId }
                if (currentSession == null || currentSession.status == SessionStatus.SUCCESS || currentSession.status == SessionStatus.FAILED) {
                    break
                }
            }

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
        
        udpReceiveJob?.cancel()
        udpSendJob?.cancel()
        tcpServerJob?.cancel()
        
        stopNsdDiscoveryAndRegistration()
        
        try {
            tcpServerSocket?.close()
        } catch (ignored: Exception) {}
        
        super.onCleared()
    }
}
