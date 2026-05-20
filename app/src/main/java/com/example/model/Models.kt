package com.example.model

import androidx.compose.ui.graphics.vector.ImageVector

data class Device(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isPaired: Boolean = false,
    val protocols: List<String>,
    val signalStrength: Float, // 0f to 1f
    val osName: String = "Android 14"
)

enum class Protocol(val id: String, val displayName: String) {
    BLE("ble", "Bluetooth LE"),
    NSD("nsd", "mDNS / NSD"),
    UDP("udp", "UDP Broadcast"),
    WIFI_DIRECT("wifi_direct", "WiFi Direct")
}

data class LocalFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val category: String, // "Images", "Videos", "Documents"
    val dateAdded: String,
    val description: String = "",
    val thumbnailId: Int? = null // Dummy or representation
) {
    val sizeFormatted: String get() = formatSize(sizeBytes)
}

data class TransferItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    var progressBytes: Long = 0,
    var isCompleted: Boolean = false,
    var isFailed: Boolean = false
) {
    val sizeFormatted: String get() = formatSize(sizeBytes)
    val progressPercent: Float get() = if (sizeBytes > 0) progressBytes.toFloat() / sizeBytes else 0f
}

data class TransferSession(
    val id: String,
    val remoteDevice: Device,
    val isSending: Boolean, // True = sending, False = receiving
    val files: List<TransferItem>,
    var status: SessionStatus = SessionStatus.ONGOING,
    var currentSpeedBytesPerSecond: Long = 0,
    var secondsElapsed: Int = 0
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    val progressBytes: Long get() = files.sumOf { it.progressBytes }
    val progressPercent: Float get() = if (totalBytes > 0) progressBytes.toFloat() / totalBytes else 0f
    
    val speedFormatted: String get() = "${formatSize(currentSpeedBytesPerSecond)}/s"
    val etaSeconds: Int get() {
        if (currentSpeedBytesPerSecond <= 0) return -1
        val remainingBytes = totalBytes - progressBytes
        return (remainingBytes / currentSpeedBytesPerSecond).toInt()
    }
}

enum class SessionStatus {
    ONGOING,
    PAUSED,
    SUCCESS,
    FAILED
}

data class HistoryRecord(
    val id: String,
    val deviceName: String,
    val isSent: Boolean,
    val fileNameSummary: String,
    val totalSize: Long,
    val timeString: String,
    val isSuccess: Boolean,
    val speedString: String,
    val durationSeconds: Int
) {
    val sizeFormatted: String get() = formatSize(totalSize)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}
