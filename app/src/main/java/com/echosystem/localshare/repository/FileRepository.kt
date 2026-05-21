package com.echosystem.localshare.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context
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
        val downloadDir = File(context.getExternalFilesDir(null), "Received")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        
        val targetFile = File(downloadDir, fileName)
        FileOutputStream(targetFile).use { output ->
            inputStream.copyTo(output)
        }
        return targetFile
    }
}
