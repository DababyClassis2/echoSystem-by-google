package com.echosystem.localshare.model

import kotlinx.serialization.*

@Serializable
sealed class ServerEvent {
    @Serializable
    @SerialName("device_joined")
    data class DeviceJoined(val device: Device) : ServerEvent()

    @Serializable
    @SerialName("device_left")
    data class DeviceLeft(val deviceId: String) : ServerEvent()

    @Serializable
    @SerialName("file_received")
    data class FileReceived(val file: SharedFile) : ServerEvent()

    @Serializable
    @SerialName("transfer_progress")
    data class TransferProgress(val sessionId: String, val progress: Float, val speed: Long) : ServerEvent()

    @Serializable
    @SerialName("ping")
    data object Ping : ServerEvent()
}

@Serializable
data class SharedFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val senderName: String,
    val timestamp: Long
)
