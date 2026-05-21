package com.echosystem.localshare.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.echosystem.localshare.model.LocalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

class HttpWebServer(
    private val context: Context,
    private val allFilesFlow: StateFlow<List<LocalFile>>,
    private val onUploadSuccess: (LocalFile) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var activePort: Int = 8085
        private set

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                // Find an available port, starting around 8085
                var port = 8085
                var success = false
                while (port < 8100 && !success) {
                    try {
                        serverSocket = ServerSocket(port)
                        activePort = port
                        success = true
                    } catch (e: Exception) {
                        port++
                    }
                }
                if (!success) {
                    serverSocket = ServerSocket(0)
                    activePort = serverSocket?.localPort ?: 8085
                }

                android.util.Log.d("HttpWebServer", "Web Share Server listening on port $activePort")

                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch(Dispatchers.IO) {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HttpWebServer", "Web server loop stopped: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (ignored: Exception) {}
        serverJob?.cancel()
    }

    private fun handleClient(socket: Socket) {
        var reader: BufferedReader? = null
        var output: OutputStream? = null
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = BufferedOutputStream(socket.getOutputStream())

            val firstLine = reader.readLine() ?: return
            android.util.Log.d("HttpWebServer", "Request: $firstLine")
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]

            // Parse headers to locate Content-Length for uploads
            var contentLength = 0L
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    contentLength = line.substring(15).trim().toLongOrNull() ?: 0L
                }
            }

            if (method == "GET") {
                if (fullPath == "/" || fullPath == "/index.html") {
                    serveIndexHtml(output)
                } else if (fullPath.startsWith("/download")) {
                    val uri = Uri.parse("http://localhost$fullPath")
                    val id = uri.getQueryParameter("id")
                    if (id != null) {
                        serveDownload(id, output)
                    } else {
                        serveNotFound(output)
                    }
                } else {
                    serveNotFound(output)
                }
            } else if (method == "POST" && fullPath.startsWith("/upload")) {
                val uri = Uri.parse("http://localhost$fullPath")
                val filename = URLDecoder.decode(uri.getQueryParameter("name") ?: "shared_file_${System.currentTimeMillis()}", "UTF-8")
                handleUpload(filename, contentLength, socket.getInputStream(), output)
            } else {
                serveNotFound(output)
            }

        } catch (e: Exception) {
            android.util.Log.e("HttpWebServer", "Error processing client request: ${e.message}")
        } finally {
            try { reader?.close() } catch (ignored: Exception) {}
            try { output?.close() } catch (ignored: Exception) {}
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun serveIndexHtml(output: OutputStream) {
        val files = allFilesFlow.value
        val listHtml = StringBuilder()
        if (files.isEmpty()) {
            listHtml.append("""
                <div class="empty-state">
                    <h3>No Shared Files</h3>
                    <p>Import some files on the Android app screen to make them instantly downloadable here!</p>
                </div>
            """.trimIndent())
        } else {
            for (file in files) {
                val encodedId = URLEncoder.encode(file.id, "UTF-8")
                listHtml.append("""
                    <div class="file-item">
                        <div class="file-info">
                            <div class="file-name">${escapeHtml(file.name)}</div>
                            <div class="file-meta">${file.category} • ${file.sizeFormatted}</div>
                        </div>
                        <a href="/download?id=$encodedId" class="btn-download">Download</a>
                    </div>
                """.trimIndent())
            }
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>EchoSystem Sharing Portal</title>
                <style>
                    :root {
                        --bg: #0B0F19;
                        --card: #151F32;
                        --primary: #6366F1;
                        --primary-glow: rgba(99, 102, 241, 0.15);
                        --primary-hover: #4F46E5;
                        --text: #F1F5F9;
                        --text-muted: #94A3B8;
                        --border: #24324D;
                        --success: #10B981;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        background-color: var(--bg);
                        color: var(--text);
                        font-family: system-ui, -apple-system, sans-serif;
                        padding: 32px 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container {
                        width: 100%;
                        max-width: 600px;
                        display: flex;
                        flex-direction: column;
                        gap: 24px;
                    }
                    header {
                        text-align: center;
                        margin-bottom: 8px;
                    }
                    h1 { font-size: 28px; font-weight: 800; color: #FFFFFF; letter-spacing: -0.5px; margin-bottom: 8px; }
                    header p { color: var(--text-muted); font-size: 14px; }
                    .card {
                        background: var(--card);
                        border: 1px solid var(--border);
                        border-radius: 20px;
                        padding: 24px;
                        box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
                    }
                    h2 { font-size: 16px; font-weight: 700; color: #FFFFFF; margin-bottom: 18px; text-transform: uppercase; letter-spacing: 0.5px; }
                    .file-list { display: flex; flex-direction: column; gap: 12px; max-height: 350px; overflow-y: auto; padding-right: 4px; }
                    .file-item {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        background: rgba(11, 15, 25, 0.5);
                        border: 1px solid var(--border);
                        border-radius: 14px;
                        padding: 14px 18px;
                        transition: border-color 0.2s;
                    }
                    .file-item:hover { border-color: var(--primary); }
                    .file-info { display: flex; flex-direction: column; gap: 4px; overflow: hidden; margin-right: 12px; }
                    .file-name { font-size: 14px; font-weight: 600; color: #F8FAFC; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                    .file-meta { font-size: 12px; color: var(--text-muted); }
                    .btn-download {
                        background: var(--primary);
                        color: white;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 600;
                        padding: 8px 16px;
                        border-radius: 10px;
                        transition: background 0.15s, transform 0.1s;
                        flex-shrink: 0;
                    }
                    .btn-download:hover { background: var(--primary-hover); transform: translateY(-1px); }
                    .dropzone {
                        border: 2px dashed var(--border);
                        border-radius: 16px;
                        padding: 40px 24px;
                        text-align: center;
                        cursor: pointer;
                        transition: border-color 0.2s, background 0.2s;
                    }
                    .dropzone:hover { border-color: var(--primary); background: var(--primary-glow); }
                    .dropzone input { display: none; }
                    .dropzone p { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
                    .dropzone span { font-size: 12px; color: var(--text-muted); }
                    .progress-bar {
                        width: 100%;
                        height: 6px;
                        background: rgba(11, 15, 25, 0.8);
                        border-radius: 3px;
                        margin-top: 16px;
                        overflow: hidden;
                        display: none;
                    }
                    .progress-fill { height: 100%; width: 0%; background: var(--success); transition: width 0.1s; }
                    .empty-state { text-align: center; padding: 24px; color: var(--text-muted); }
                    .empty-state h3 { font-size: 15px; font-weight: 600; margin-bottom: 4px; color: #F1F5F9; }
                    .empty-state p { font-size: 13px; }
                    .toast {
                        position: fixed;
                        bottom: 32px;
                        background: var(--success);
                        color: white;
                        padding: 14px 28px;
                        border-radius: 12px;
                        font-size: 14px;
                        font-weight: 700;
                        box-shadow: 0 10px 25px -5px rgba(16, 185, 129, 0.4);
                        display: none;
                        animation: slideUp 0.3s ease-out;
                    }
                    @keyframes slideUp {
                        from { transform: translateY(20px); opacity: 0; }
                        to { transform: translateY(0); opacity: 1; }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <header>
                        <h1>EchoSystem Web Share 🌌</h1>
                        <p>High-speed, zero-complication local P2P network web sharing platform</p>
                    </header>

                    <div class="card">
                        <h2>📥 Files from Phone</h2>
                        <div class="file-list">
                            $listHtml
                        </div>
                    </div>

                    <div class="card">
                        <h2>📤 Upload directly to Phone</h2>
                        <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                            <input type="file" id="fileInput" name="file" onchange="uploadFile(this.files[0])">
                            <p><b>Drag & drop files here</b> or click to browser</p>
                            <span>Files stream directly to the phone's downloads directory in real-time</span>
                            <div class="progress-bar" id="progressBar"><div class="progress-fill" id="progressFill"></div></div>
                        </div>
                    </div>
                </div>

                <div class="toast" id="toast">Successfully completed upload!</div>

                <script>
                    const dropzone = document.getElementById('dropzone');

                    window.addEventListener('dragover', (e) => e.preventDefault());
                    window.addEventListener('drop', (e) => e.preventDefault());

                    dropzone.addEventListener('dragover', (e) => {
                        e.preventDefault();
                        dropzone.style.borderColor = 'var(--primary)';
                    });
                    dropzone.addEventListener('dragleave', (e) => {
                        e.preventDefault();
                        dropzone.style.borderColor = 'var(--border)';
                    });
                    dropzone.addEventListener('drop', (e) => {
                        e.preventDefault();
                        dropzone.style.borderColor = 'var(--border)';
                        if (e.dataTransfer.files.length > 0) {
                            uploadFile(e.dataTransfer.files[0]);
                        }
                    });

                    function uploadFile(file) {
                        if (!file) return;
                        const bar = document.getElementById('progressBar');
                        const fill = document.getElementById('progressFill');
                        bar.style.display = 'block';
                        fill.style.width = '0%';

                        const xhr = new XMLHttpRequest();
                        xhr.open('POST', '/upload?name=' + encodeURIComponent(file.name), true);

                        xhr.upload.onprogress = (e) => {
                            if (e.lengthComputable) {
                                const pct = (e.loaded / e.total) * 100;
                                fill.style.width = pct + '%';
                            }
                        };

                        xhr.onload = () => {
                            if (xhr.status === 200) {
                                showToast('File received: ' + file.name);
                                setTimeout(() => { window.location.reload(); }, 1200);
                            } else {
                                alert('Failed to transmit file.');
                            }
                        };

                        xhr.send(file);
                    }

                    function showToast(msg) {
                        const t = document.getElementById('toast');
                        t.textContent = msg;
                        t.style.display = 'block';
                        setTimeout(() => { t.style.display = 'none'; }, 2800);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        val response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${html.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" +
                html

        output.write(response.toByteArray())
        output.flush()
    }

    private fun serveDownload(id: String, output: OutputStream) {
        val file = allFilesFlow.value.firstOrNull { it.id == id }
        if (file == null || file.uriString == null) {
            serveNotFound(output)
            return
        }

        try {
            val contentUri = Uri.parse(file.uriString)
            val inputStream = context.contentResolver.openInputStream(contentUri)
            if (inputStream == null) {
                serveNotFound(output)
                return
            }

            val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: ${file.sizeBytes}\r\n" +
                    "Content-Disposition: attachment; filename=\"${escapeFilename(file.name)}\"\r\n" +
                    "Connection: close\r\n\r\n"

            output.write(headers.toByteArray())
            output.flush()

            val buffer = ByteArray(4096 * 8)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
            inputStream.close()
        } catch (e: Exception) {
            android.util.Log.e("HttpWebServer", "Download failed: ${e.message}")
        }
    }

    private fun handleUpload(filename: String, contentLength: Long, input: InputStream, output: OutputStream) {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadsDir, filename)

        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(targetFile)
            val buffer = ByteArray(4096 * 8)
            var totalRead = 0L
            var read: Int

            while (totalRead < contentLength) {
                val toRead = minOf(contentLength - totalRead, buffer.size.toLong()).toInt()
                read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fileOutputStream.write(buffer, 0, read)
                totalRead += read
            }

            fileOutputStream.flush()

            // Map extension to proper file list categories
            val extension = filename.substringAfterLast(".", "").lowercase()
            val category = when (extension) {
                "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "Images"
                "mp4", "mkv", "avi", "mov", "webm", "3gp" -> "Videos"
                "mp3", "wav", "m4a", "aac", "ogg", "flac" -> "Audio"
                else -> "Documents"
            }
            val size = targetFile.length()

            val localFile = LocalFile(
                id = Uri.fromFile(targetFile).toString(),
                name = filename,
                sizeBytes = if (size > 0) size else 1024L,
                category = category,
                dateAdded = "Just Received",
                description = "Uploaded via Browser Portal",
                uriString = Uri.fromFile(targetFile).toString()
            )

            onUploadSuccess(localFile)

            val successResponse = "HTTP/1.1 200 OK\r\nConnection: close\r\nContent-Length: 2\r\n\r\nOK"
            output.write(successResponse.toByteArray())
            output.flush()

        } catch (e: Exception) {
            android.util.Log.e("HttpWebServer", "Failed receiving upload: ${e.message}")
            val errorResponse = "HTTP/1.1 500 Internal Server Error\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            try { output.write(errorResponse.toByteArray()) } catch (ignored: Exception) {}
        } finally {
            try { fileOutputStream?.close() } catch (ignored: Exception) {}
        }
    }

    private fun serveNotFound(output: OutputStream) {
        val body = "404 Not Found"
        val response = "HTTP/1.1 404 Not Found\r\n" +
                "Connection: close\r\n" +
                "Content-Length: ${body.length}\r\n\r\n" +
                body
        output.write(response.toByteArray())
        output.flush()
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    private fun escapeFilename(str: String): String {
        return str.replace("\"", "\\\"")
    }
}
