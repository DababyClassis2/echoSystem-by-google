package com.echosystem.localshare.web

import android.content.Context
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.io.File

class WebShareServer(private val context: Context) {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val port = 8989

    fun start() {
        if (server != null) return

        server = embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    call.respondText(loadDashboardHtml(), ContentType.Text.Html)
                }

                get("/files") {
                    val files = getSharedFiles()
                    // Manually serialize files to JSON Array string to prevent serialization errors
                    val json = files.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                    call.respondText(json, ContentType.Application.Json)
                }

                get("/download/{filename}") {
                    val name = call.parameters["filename"] ?: return@get
                    var file = File(context.filesDir, name)
                    if (!file.exists()) {
                        val receivedDir = File(context.getExternalFilesDir(null), "Received")
                        file = File(receivedDir, name)
                    }
                    if (file.exists()) {
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    private fun loadDashboardHtml(): String {
        return try {
            context.assets.open("web/dashboard.html")
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // High-fidelity fallback HTML if asset is not loaded
            FALLBACK_DASHBOARD_HTML
        }
    }

    private fun getSharedFiles(): List<String> {
        val filesList = mutableListOf<String>()
        context.filesDir.list()?.toList()?.let { filesList.addAll(it) }
        val receivedDir = File(context.getExternalFilesDir(null), "Received")
        if (receivedDir.exists()) {
            receivedDir.list()?.toList()?.let { filesList.addAll(it) }
        }
        return filesList.distinct()
    }

    companion object {
        private const val FALLBACK_DASHBOARD_HTML = """<!DOCTYPE html>
<html>
<head>
    <title>LocalShare Web</title>
    <style>
        body { font-family: sans-serif; padding: 20px; background-color: #f5f5f5; color: #333; }
        .file { padding: 12px; border-bottom: 1px solid #ddd; background: white; margin-bottom: 8px; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .title { font-size: 24px; margin-bottom: 20px; font-weight: bold; }
        a { color: #3f51b5; text-decoration: none; font-weight: 500; }
    </style>
</head>
<body>
    <div class="title">LocalShare Web Dashboard</div>
    <div id="fileList">Loading files...</div>

    <script>
        async function loadFiles() {
            try {
                const res = await fetch('/files');
                const files = await res.json();
                const container = document.getElementById('fileList');
                container.innerHTML = '';

                if (files.length === 0) {
                    container.innerHTML = '<div style="color: #666;">No files currently shared.</div>';
                    return;
                }

                files.forEach(f => {
                    const div = document.createElement('div');
                    div.className = 'file';
                    div.innerHTML = `<a href="/download/${"$"}{encodeURIComponent(f)}">${"$"}{f}</a>`;
                    container.appendChild(div);
                });
            } catch (e) {
                document.getElementById('fileList').textContent = 'Error loading files.';
            }
        }

        loadFiles();
    </script>
</body>
</html>"""
    }
}
