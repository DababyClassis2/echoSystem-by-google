package com.echosystem.localshare.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.echosystem.localshare.core.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: StorageManager
) {
    fun getFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.path?.let { File(it).name }
    }

    fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index != -1) {
                    size = it.getLong(index)
                }
            }
        }
        return size
    }

    fun openInputStream(uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    fun saveFile(fileName: String, inputStream: InputStream): File {
        val downloadDir = getReceivedFolder()
        if (!downloadDir.exists()) downloadDir.mkdirs()
        
        val targetFile = File(downloadDir, fileName)
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }
        return targetFile
    }

    fun getReceivedFiles(): List<File> {
        val downloadDir = getReceivedFolder()
        if (!downloadDir.exists()) return emptyList()
        return downloadDir.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    fun deleteReceivedFile(fileName: String): Boolean {
        val downloadDir = getReceivedFolder()
        val targetFile = File(downloadDir, fileName)
        return if (targetFile.exists()) {
            targetFile.delete()
        } else {
            false
        }
    }

    fun getReceivedFilesDir(): File {
        val downloadDir = storageManager.rootDir
        if (!downloadDir.exists()) downloadDir.mkdirs()
        return downloadDir
    }

    fun getReceivedFolder(): File {
        val folder = File(storageManager.rootDir, "Received")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }
}
