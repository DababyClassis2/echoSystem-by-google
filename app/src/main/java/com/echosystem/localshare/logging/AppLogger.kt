package com.echosystem.localshare.logging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "AppLogger"
    private var logDir: File? = null

    fun initialize(context: Context) {
        val appDir = context.getExternalFilesDir(null) ?: context.filesDir
        logDir = File(appDir, "logs").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        logEvent(TAG, "Logger Initialized successfully from application class")
        initCrashHandler(context)
    }

    private fun getLogsDirectory(): File {
        val dir = logDir ?: File(System.getProperty("java.io.tmpdir") ?: "/")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getEventsFile(): File {
        return File(getLogsDirectory(), "app_events.txt")
    }

    fun getCrashesFile(): File {
        return File(getLogsDirectory(), "app_crashes.txt")
    }

    @Synchronized
    fun logEvent(tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] [$tag]: $message\n"
        
        // Print to logcat so it is visible in developer tools too
        Log.d(tag, message)
        
        try {
            val file = getEventsFile()
            file.appendText(logLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to events log: ${e.message}")
        }
    }

    @Synchronized
    fun logCrash(throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        
        val crashLog = "========================================\n" +
                "CRASH TIMESTAMP: $timestamp\n" +
                "THREAD Name: ${Thread.currentThread().name}\n" +
                "EXCEPTION: ${throwable.localizedMessage}\n" +
                "STACK TRACE:\n$stackTrace" +
                "========================================\n\n"

        Log.e(TAG, "Application Crash Intercepted! Writing stack trace to crash dump.")
        
        try {
            val file = getCrashesFile()
            file.appendText(crashLog)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing crash log file: ${e.message}")
        }
    }

    fun readEventsLog(): String {
        return try {
            val file = getEventsFile()
            if (file.exists()) file.readText() else "No event logs present yet."
        } catch (e: Exception) {
            "Error reading event logs: ${e.localizedMessage}"
        }
    }

    fun readCrashesLog(): String {
        return try {
            val file = getCrashesFile()
            if (file.exists()) file.readText() else "No application crash logs recorded."
        } catch (e: Exception) {
            "Error reading crash logs: ${e.localizedMessage}"
        }
    }

    fun clearLogs() {
        try {
            val eventsFile = getEventsFile()
            if (eventsFile.exists()) eventsFile.delete()
            val crashesFile = getCrashesFile()
            if (crashesFile.exists()) crashesFile.delete()
            logEvent(TAG, "All application logs cleared by user.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs: ${e.message}")
        }
    }

    fun exportLogs(context: Context) {
        try {
            logEvent(TAG, "User requested sharing files app diagnostics")
            val eventsFile = getEventsFile()
            val crashesFile = getCrashesFile()
            
            val filesList = ArrayList<Uri>()
            val authority = "${context.packageName}.fileprovider"

            if (eventsFile.exists()) {
                val uri = FileProvider.getUriForFile(context, authority, eventsFile)
                filesList.add(uri)
            }
            if (crashesFile.exists()) {
                val uri = FileProvider.getUriForFile(context, authority, crashesFile)
                filesList.add(uri)
            }

            if (filesList.isEmpty()) {
                Log.w(TAG, "No files available to share")
                return
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesList)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share App Logs & Diagnostics"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share logs: ${e.message}")
        }
    }

    private fun initCrashHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Catch error
            logCrash(throwable)
            // Delegate call back to the system/original handler for visual crash display
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
