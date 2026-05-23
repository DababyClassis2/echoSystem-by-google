package com.echosystem.localshare.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class PermissionResult {
    object Granted : PermissionResult()
    data class Denied(val reason: String) : PermissionResult()
}

data class FileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

@Singleton
class StorageManager @Inject constructor() {
    private val tag = "StorageManager"
    
    val rootDir: File
        get() = File(Environment.getExternalStorageDirectory(), "echoSystem")

    init {
        ensureFolders()
    }

    fun ensureFolders() {
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }
            listOf("Received", "Sent", "Shared").forEach { sub ->
                val folder = File(rootDir, sub)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun ensurePermissions(context: Context): PermissionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                ensureFolders()
                return PermissionResult.Granted
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        return PermissionResult.Denied("Failed to launch MANAGE_EXTERNAL_STORAGE Settings UI")
                    }
                }
                return PermissionResult.Denied("MANAGE_EXTERNAL_STORAGE permission required")
            }
        } else {
            val hasWrite = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasWrite) {
                ensureFolders()
                return PermissionResult.Granted
            } else {
                return PermissionResult.Denied("WRITE_EXTERNAL_STORAGE permission required")
            }
        }
    }

    fun scanDirectory(path: String): List<FileItem> {
        return try {
            val dir = if (path.isEmpty()) rootDir else File(rootDir, path)
            if (!dir.exists() || !dir.isDirectory || !dir.absolutePath.startsWith(rootDir.absolutePath)) {
                return emptyList()
            }
            val files = dir.listFiles()?.toList() ?: emptyList()
            files.map { file ->
                FileItem(
                    name = file.name,
                    absolutePath = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified()
                )
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
