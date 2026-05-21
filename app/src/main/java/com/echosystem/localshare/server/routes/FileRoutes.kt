package com.echosystem.localshare.server.routes

import com.echosystem.localshare.model.ServerEvent
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.security.PairingManager
import com.echosystem.localshare.server.ServerEventBus
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable
data class FileWebResponse(val name: String, val size: Long, val formattedSize: String)

fun Route.fileRoutes(
    fileRepository: FileRepository,
    serverEventBus: ServerEventBus,
    pairingManager: PairingManager
) {
    // 1. Serve Single-Page Web Portal HTML
    get("/") {
        call.respondText(HTML_PORTAL, ContentType.Text.Html)
    }

    // 2. Query Available Downloadable Files
    get("/web/files") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Pairing authentication required")
            return@get
        }

        val files = fileRepository.getReceivedFiles()
        val response = files.map { file ->
            val size = file.length()
            val formatted = when {
                size <= 0 -> "--"
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${String.format("%.1f", size / 1024f)} KB"
                else -> "${String.format("%.1f", size / (1024f * 1024f))} MB"
            }
            FileWebResponse(file.name, size, formatted)
        }
        call.respondText(Json.encodeToString(response), ContentType.Application.Json)
    }

    // 3. Download File Stream
    get("/web/download") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        val fileName = call.parameters["fileName"] ?: ""

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Access unauthorized")
            return@get
        }

        if (fileName.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@get
        }

        val receivedDir = fileRepository.getReceivedFilesDir()
        val file = File(receivedDir, fileName)

        if (!file.exists() || !file.isFile) {
            call.respond(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${file.name}\"")
        call.respondFile(file)
    }

    // 4. Handle Secure Browser Direct API Upload
    post("/web/upload") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Device connection not verified")
            return@post
        }

        try {
            val multipart = call.receiveMultipart()
            var fileName = "received_web_file"
            val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0L

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileName = part.originalFileName ?: "file_${System.currentTimeMillis()}"
                    
                    val file = withContext(Dispatchers.IO) {
                        val downloadDir = fileRepository.getReceivedFilesDir()
                        val targetFile = File(downloadDir, fileName)

                        // Broadcast Transfer Started to the UI in real-time
                        serverEventBus.emit(ServerEvent.TransferStarted(fileName, fileName, contentLength))

                        part.streamProvider().use { input ->
                            FileOutputStream(targetFile).use { output ->
                                val buffer = ByteArray(1024 * 64)
                                var bytesReadTotal = 0L
                                var lastNotificationTime = 0L
                                
                                var bytes = input.read(buffer)
                                while (bytes >= 0) {
                                    output.write(buffer, 0, bytes)
                                    bytesReadTotal += bytes
                                    
                                    val currentTime = System.currentTimeMillis()
                                    if (contentLength > 0 && currentTime - lastNotificationTime > 100) {
                                        val progress = bytesReadTotal.toFloat() / contentLength
                                        serverEventBus.emit(ServerEvent.TransferProgress(fileName, progress))
                                        lastNotificationTime = currentTime
                                    }
                                    bytes = input.read(buffer)
                                }
                            }
                        }
                        // Finalize status
                        serverEventBus.emit(ServerEvent.TransferCompleted(fileName))
                        targetFile
                    }
                }
                part.dispose()
            }
            call.respondText("Successfully uploaded file into Received directory")
        } catch (e: Exception) {
            serverEventBus.emit(ServerEvent.TransferFailed("Web upload failed", e.localizedMessage ?: "Unknown stream error"))
            call.respond(HttpStatusCode.InternalServerError, e.localizedMessage ?: "Upload stream failed")
        }
    }

    // 5. Delete specific file
    post("/web/delete") {
        val pin = call.request.headers["X-PIN"] ?: call.parameters["pin"] ?: ""
        val deviceId = call.request.headers["X-Device-Id"] ?: call.parameters["deviceId"] ?: ""
        val fileName = call.parameters["fileName"] ?: ""

        if (!pairingManager.verifyPin(pin) && !pairingManager.isPaired(deviceId)) {
            call.respond(HttpStatusCode.Unauthorized, "Access unauthorized")
            return@post
        }

        if (fileName.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@post
        }

        val deleted = fileRepository.deleteReceivedFile(fileName)
        if (deleted) {
            call.respondText("Deleted successfully")
        } else {
            call.respond(HttpStatusCode.NotFound, "File or directory not found")
        }
    }
}

// Beautiful Web Portal Single-Page-App with dark mode slate style
private val HTML_PORTAL = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LocalShare Web Portal</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <style>
        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
            background: radial-gradient(circle at top, #0f172a 0%, #020617 100%);
        }
    </style>
</head>
<body class="text-slate-100 min-h-screen flex flex-col items-center">
    <div class="w-full max-w-5xl px-4 py-8 flex flex-col h-full flex-grow">
        <!-- Brand Title Header -->
        <header class="flex items-center justify-between border-b border-slate-800 pb-6 mb-8">
            <div class="flex items-center space-x-3">
                <div class="bg-indigo-600/20 text-indigo-400 p-2.5 rounded-2xl border border-indigo-500/20">
                    <svg class="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                    </svg>
                </div>
                <div>
                     <h1 class="font-extrabold text-2xl tracking-tight text-white mb-0.5">LocalShare</h1>
                     <p class="text-xs text-slate-400 font-medium">Peer-to-Peer Secure sharing portal</p>
                </div>
            </div>
            <!-- Verification Indicator -->
            <div id="connectionStatus" class="flex items-center space-x-2 bg-slate-800/50 border border-slate-700/50 px-4 py-1.5 rounded-full text-xs font-semibold text-slate-400">
                <span id="statusDot" class="w-2 h-2 rounded-full bg-red-500 animate-pulse"></span>
                <span id="statusText">Unauthorized Node</span>
            </div>
        </header>

        <!-- Unverified/Verification Section -->
        <section id="authSection" class="my-auto max-w-md w-full mx-auto bg-slate-900/60 border border-slate-800 rounded-3xl p-8 backdrop-blur-sm shadow-2xl relative overflow-hidden flex flex-col items-center">
            <!-- Decorative circle -->
            <div class="absolute -top-16 -right-16 w-36 h-36 rounded-full bg-indigo-600/10 blur-xl"></div>
            
            <div class="bg-indigo-500/10 text-indigo-400 p-4 rounded-full border border-indigo-500/20 mb-6">
                <!-- Shield Lock Icon -->
                <svg class="w-10 h-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                </svg>
            </div>

            <h2 class="text-2xl font-extrabold text-white text-center mb-2">Security Verification</h2>
            <p class="text-sm text-slate-400 text-center mb-6 leading-relaxed">Enter the 6-digit Security Pin Key displayed on the phone app interface settings tab.</p>

            <form id="authForm" class="w-full space-y-4">
                <div class="relative">
                    <input 
                        type="text" 
                        maxLength="6" 
                        pattern="[0-9]{6}"
                        id="pinInput" 
                        placeholder="123456" 
                        class="w-full text-center tracking-[0.5em] font-extrabold text-2xl text-white bg-slate-950/60 border border-slate-800 hover:border-slate-700 focus:border-indigo-500 rounded-2xl h-16 outline-none transition-all duration-300 placeholder:tracking-normal placeholder:font-semibold placeholder:text-slate-600"
                        required
                    />
                </div>
                <button 
                    type="submit" 
                    class="w-full bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white font-bold h-12 rounded-2xl transition duration-200 flex items-center justify-center space-x-2 shadow-lg shadow-indigo-600/20"
                >
                    <span>Connect & Unlock Portal</span>
                </button>
                <p id="authError" class="text-xs text-red-400 font-semibold text-center mt-2 hidden"></p>
            </form>
        </section>

        <!-- Main Dashboard Layout -->
        <main id="dashboard" class="grid grid-cols-1 lg:grid-cols-12 gap-8 flex-grow hidden">
            <!-- Left Panel: Uplink Area -->
            <section class="lg:col-span-5 bg-slate-900/40 border border-slate-800 p-6 rounded-3xl flex flex-col">
                <h3 class="font-bold text-lg text-white mb-4 flex items-center">
                    <svg class="w-5 h-5 text-indigo-400 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                    </svg>
                    Send Files to Device
                </h3>

                <!-- Upload Drag-Drop Box -->
                <div 
                    id="dropzone"
                    class="border-2 border-dashed border-slate-800 hover:border-indigo-500/60 bg-slate-950/40 rounded-2xl p-8 py-12 flex flex-col items-center justify-center text-center cursor-pointer transition-all duration-300 relative group"
                >
                    <input type="file" id="fileSelector" class="hidden" multiple />
                    
                    <div class="bg-indigo-600/10 text-indigo-400 p-4 rounded-xl border border-indigo-500/10 group-hover:scale-110 mb-4 transition-transform duration-300">
                        <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                        </svg>
                    </div>

                    <p class="font-bold text-sm text-slate-200 mb-1">Drag files here, or <span class="text-indigo-400 select-none">browse files</span></p>
                    <p class="text-xs text-slate-500 font-medium">Supports multiple files of arbitrary size directly in LAN</p>
                </div>

                <!-- Active Upload Lists -->
                <div class="flex-grow mt-6 flex flex-col">
                    <h4 class="text-xs font-bold text-slate-500 uppercase tracking-wider mb-3">Transmission Queue</h4>
                    <div id="queueList" class="space-y-3 flex-grow overflow-y-auto max-h-[300px] pr-1">
                        <!-- Empty queue state -->
                        <div id="emptyQueue" class="text-center py-8 text-slate-600 text-xs font-medium border border-slate-900 rounded-xl bg-slate-950/20">
                            Queue is empty. Select files above to transmit.
                        </div>
                    </div>
                </div>
            </section>

            <!-- Right Panel: Download Portal library -->
            <section class="lg:col-span-7 bg-slate-900/40 border border-slate-800 p-6 rounded-3xl flex flex-col h-full min-h-[500px]">
                <div class="flex items-center justify-between mb-6">
                    <h3 class="font-bold text-lg text-white flex items-center">
                        <svg class="w-5 h-5 text-indigo-400 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
                        </svg>
                        Storage Ledger Repository
                    </h3>
                    
                    <button 
                        id="refreshLibrary" 
                        class="text-indigo-400 hover:text-indigo-300 border border-slate-800 hover:border-slate-700 bg-slate-950/40 p-2 rounded-xl text-xs font-medium transition duration-200 flex items-center space-x-1.5"
                    >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 1121.21 4H4" />
                        </svg>
                        <span>Sync Repository</span>
                    </button>
                </div>

                <!-- Storage Files Library table layout -->
                <div class="flex-grow overflow-y-auto max-h-[520px] pr-2">
                    <div id="libraryContainer" class="space-y-3">
                        <!-- Items rendered via script -->
                    </div>
                </div>
            </section>
        </main>
    </div>

    <!-- Script Block containing logical flow -->
    <script>
        let pairingPin = localStorage.getItem('pairingPin') || '';
        let deviceId = localStorage.getItem('deviceId') || '';

        // Generate dynamic device ID to authorize device session on memory
        if (!deviceId) {
            deviceId = 'web-client-' + Math.random().toString(36).substring(2, 10);
            localStorage.setItem('deviceId', deviceId);
        }

        const authSection = document.getElementById('authSection');
        const dashboard = document.getElementById('dashboard');
        const connectionStatus = document.getElementById('connectionStatus');
        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        const authForm = document.getElementById('authForm');
        const pinInput = document.getElementById('pinInput');
        const authError = document.getElementById('authError');
        const dropzone = document.getElementById('dropzone');
        const fileSelector = document.getElementById('fileSelector');
        const queueList = document.getElementById('queueList');
        const emptyQueue = document.getElementById('emptyQueue');
        const libraryContainer = document.getElementById('libraryContainer');
        const refreshLibrary = document.getElementById('refreshLibrary');

        // Check authentication status on startup
        async function checkConnection() {
            if (!pairingPin) {
                showAuth();
                return;
            }

            try {
                const response = await fetch('/web/files', {
                    headers: {
                        'X-PIN': pairingPin,
                        'X-Device-Id': deviceId
                    }
                });

                if (response.ok) {
                    showDashboard();
                    loadLibrary();
                } else {
                    showAuth();
                }
            } catch (e) {
                showAuth();
            }
        }

        function showAuth() {
            authSection.classList.remove('hidden');
            dashboard.classList.add('hidden');
            statusDot.className = "w-2 h-2 rounded-full bg-red-500 animate-pulse";
            statusText.textContent = "Unauthorized Node";
        }

        function showDashboard() {
            authSection.classList.add('hidden');
            dashboard.classList.remove('hidden');
            statusDot.className = "w-2 h-2 rounded-full bg-emerald-500";
            statusText.textContent = "Paired Connected";
        }

        // Authenticate pairing PIN
        authForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const pin = pinInput.value.trim();
            if (pin.length < 6) return;

            authError.classList.add('hidden');

            try {
                const response = await fetch('/pairing/request', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ deviceId: deviceId, pin: pin })
                });

                if (response.ok) {
                    pairingPin = pin;
                    localStorage.setItem('pairingPin', pin);
                    showDashboard();
                    loadLibrary();
                } else {
                    authError.textContent = "Invalid companion security PIN. Refusing Node link.";
                    authError.classList.remove('hidden');
                }
            } catch (err) {
                authError.textContent = "Unable to connect with target node client.";
                authError.classList.remove('hidden');
            }
        });

        // Triggering file browser on click
        dropzone.addEventListener('click', () => fileSelector.click());

        // File Selector handling
        fileSelector.addEventListener('change', () => {
            const files = Array.from(fileSelector.files);
            uploadFiles(files);
        });

        // Drop handling
        ['dragenter', 'dragover'].forEach(eventName => {
            dropzone.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropzone.classList.add('border-indigo-500', 'bg-indigo-600/5');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropzone.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropzone.classList.remove('border-indigo-500', 'bg-indigo-600/5');
            }, false);
        });

        dropzone.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = Array.from(dt.files);
            uploadFiles(files);
        });

        function formatBytes(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
        }

        // Uploading Stream handler
        function uploadFiles(files) {
            if (files.length === 0) return;
            emptyQueue.classList.add('hidden');

            files.forEach(file => {
                const fileId = 'up-' + Math.random().toString(36).substring(2, 8);
                
                // Add to visible elements
                const row = document.createElement('div');
                row.id = fileId;
                row.className = "bg-slate-950/80 border border-slate-900 rounded-xl p-4 flex flex-col space-y-2";
                row.innerHTML = `
                    <div class="flex items-center justify-between">
                        <div class="flex items-center space-x-2.5 min-w-0">
                            <!-- File Icon SVG -->
                            <svg class="w-5 h-5 text-slate-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            <span class="text-xs font-semibold text-slate-200 truncate pr-2 max-w-[200px] md:max-w-xs">${"$"}{file.name}</span>
                        </div>
                        <span class="text-[10px] text-slate-500 font-bold" id="size-${"$"}{fileId}">${"$"}{formatBytes(file.size)}</span>
                    </div>
                    <div class="flex items-center space-x-3">
                        <div class="flex-grow bg-slate-800 rounded-full h-2 overflow-hidden">
                            <div id="bar-${"$"}{fileId}" class="bg-indigo-600 h-full w-[0%] transition-all duration-100 rounded-full"></div>
                        </div>
                        <span id="percent-${"$"}{fileId}" class="text-[10px] font-bold text-slate-400 w-8 text-right shrink-0">0%</span>
                    </div>
                `;
                queueList.appendChild(row);

                // Start transmission
                const xhr = new XMLHttpRequest();
                const formData = new FormData();
                formData.append('file', file);

                xhr.open('POST', '/web/upload', true);
                xhr.setRequestHeader('X-PIN', pairingPin);
                xhr.setRequestHeader('X-Device-Id', deviceId);

                // Progress update callback
                xhr.upload.addEventListener('progress', (e) => {
                    if (e.lengthComputable) {
                        const percent = Math.round((e.loaded / e.total) * 100);
                        document.getElementById(`bar-${"$"}{fileId}`).style.width = percent + '%';
                        document.getElementById(`percent-${"$"}{fileId}`).textContent = percent + '%';
                    }
                });

                xhr.onreadystatechange = () => {
                    if (xhr.readyState === XMLHttpRequest.DONE) {
                        if (xhr.status === 200) {
                            // Display Successful animation checkmark
                            document.getElementById(`percent-${"$"}{fileId}`).className = "text-[10px] font-bold text-emerald-400 w-8 text-right shrink-0 flex items-center justify-end";
                            document.getElementById(`percent-${"$"}{fileId}`).innerHTML = `
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
                                </svg>
                            `;
                            document.getElementById(`bar-${"$"}{fileId}`).className = "bg-emerald-500 h-full w-[100%] rounded-full";
                            loadLibrary(); // Reload list on successful receive
                        } else {
                            // Display Failed symbol
                            document.getElementById(`percent-${"$"}{fileId}`).className = "text-[10px] font-bold text-red-400 w-8 text-right shrink-0 flex items-center justify-end";
                            document.getElementById(`percent-${"$"}{fileId}`).innerHTML = `
                                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            `;
                            document.getElementById(`bar-${"$"}{fileId}`).className = "bg-red-500 h-full w-[100%] rounded-full";
                        }
                    }
                };

                xhr.send(formData);
            });
        }

        // Fetch companion received files
        async function loadLibrary() {
            try {
                const response = await fetch('/web/files', {
                    headers: {
                        'X-PIN': pairingPin,
                        'X-Device-Id': deviceId
                    }
                });

                if (response.ok) {
                    const files = await response.json();
                    renderLibrary(files);
                } else if (response.status === 401) {
                    showAuth();
                }
            } catch (err) {
                console.error("Failed syncing file library ledger", err);
            }
        }

        // Render storage library
        function renderLibrary(files) {
            libraryContainer.innerHTML = '';
            
            if (files.length === 0) {
                libraryContainer.innerHTML = `
                    <div class="text-center py-16 text-slate-600 font-medium">
                        <svg class="w-12 h-12 text-slate-800 mx-auto mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0a2 2 0 012 2v4a2 2 0 01-2 2H4a2 2 0 01-2-2v-4a2 2 0 012-2m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
                        </svg>
                        No items found on phone received storage.<br/>
                        <span class="text-xs text-slate-700">Receive list is currently empty.</span>
                    </div>
                `;
                return;
            }

            files.forEach(file => {
                const row = document.createElement('div');
                row.className = "bg-slate-900/60 border border-slate-800 hover:border-slate-800/80 rounded-2xl p-4 flex items-center justify-between transition-colors duration-200 h-[64px]";
                
                // Determine file extension icon
                const ext = file.name.substring(file.name.lastIndexOf('.') + 1).toLowerCase();
                let iconClass = "text-indigo-400";
                let iconSvg = `
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                `;

                if (['png', 'jpg', 'jpeg', 'webp', 'gif'].includes(ext)) {
                    iconClass = "text-teal-400";
                    iconSvg = `
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                        </svg>
                    `;
                } else if (['mp4', 'mkv', 'mov', 'avi'].includes(ext)) {
                    iconClass = "text-amber-400";
                    iconSvg = `
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z" />
                        </svg>
                    `;
                } else if (['mp3', 'wav', 'm4a', 'flac'].includes(ext)) {
                    iconClass = "text-pink-400";
                    iconSvg = `
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                        </svg>
                    `;
                }

                row.innerHTML = `
                    <div class="flex items-center space-x-3 min-w-0 pr-2">
                        <div class="bg-slate-950 p-2.5 rounded-xl border border-slate-800 shrink-0 ${"$"}{iconClass}">
                            ${"$"}{iconSvg}
                        </div>
                        <div class="min-w-0">
                            <p class="text-xs font-bold text-slate-200 truncate" title="${"$"}{file.name}">${"$"}{file.name}</p>
                            <span class="text-[10px] text-slate-500 font-bold">${"$"}{file.formattedSize}</span>
                        </div>
                    </div>
                    
                    <div class="flex items-center space-x-2">
                        <!-- Download btn -->
                        <a 
                            href="/web/download?fileName=${"$"}{encodeURIComponent(file.name)}&pin=${"$"}{pairingPin}&deviceId=${"$"}{deviceId}"
                            target="_blank"
                            class="bg-slate-950 hover:bg-slate-900 border border-slate-800 hover:border-slate-700 text-indigo-400 hover:text-indigo-300 p-2 rounded-xl transition-colors duration-200 shrink-0"
                            title="Download file"
                        >
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                            </svg>
                        </a>
                        
                        <!-- Delete btn -->
                        <button 
                            onclick="deleteFile('${"$"}{encodeURIComponent(file.name)}')"
                            class="bg-slate-950 hover:bg-red-950 hover:text-red-400 border border-slate-800 hover:border-red-900 text-slate-500 p-2 rounded-xl transition-colors duration-200 shrink-0"
                            title="Delete File"
                        >
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                        </button>
                    </div>
                `;
                libraryContainer.appendChild(row);
            });
        }

        // Delete File handler
        async function deleteFile(fileName) {
            if (!confirm('Are you certain you wish to delete this file permanently from companion storage?')) return;

            try {
                const response = await fetch(`/web/delete?fileName=${"$"}{fileName}`, {
                    method: 'POST',
                    headers: {
                        'X-PIN': pairingPin,
                        'X-Device-Id': deviceId
                    }
                });

                if (response.ok) {
                    loadLibrary();
                } else {
                    alert('Failed to delete file. Check connection permission.');
                }
            } catch (err) {
                console.error("Failed to delete file", err);
            }
        }

        // Connect button sync listeners
        refreshLibrary.addEventListener('click', loadLibrary);

        // Initial launch
        checkConnection();
    </script>
</body>
</html>
""".trimIndent()
