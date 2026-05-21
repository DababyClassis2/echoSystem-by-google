package com.example.repository

import android.content.Context
import com.example.model.SharedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _receivedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val receivedFiles = _receivedFiles.asStateFlow()

    private val storageDir: File by lazy {
        File(context.getExternalFilesDir(null), "Received").apply {
            if (!exists()) mkdirs()
        }
    }

    init {
        refreshFiles()
    }

    fun refreshFiles() {
        val files = storageDir.listFiles()?.map { file ->
            SharedFile(
                id = file.name,
                name = file.name,
                size = file.length(),
                mimeType = "application/octet-stream", // Simplified for now
                senderName = "LocalShare Peer",
                timestamp = file.lastModified()
            )
        } ?: emptyList()
        _receivedFiles.value = files.sortedByDescending { it.timestamp }
    }

    fun getFile(fileName: String): File = File(storageDir, fileName)
    
    fun deleteFile(fileName: String): Boolean {
        val file = getFile(fileName)
        val deleted = if (file.exists()) file.delete() else false
        if (deleted) refreshFiles()
        return deleted
    }
}
