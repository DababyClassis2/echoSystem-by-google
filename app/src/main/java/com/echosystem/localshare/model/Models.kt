package com.echosystem.localshare.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isPaired: Boolean = false,
    val status: DeviceStatus = DeviceStatus.AVAILABLE
)

enum class DeviceStatus {
    AVAILABLE, PAIRING, CONNECTED, BUSY, DISCONNECTED
}

@Serializable
data class DeviceCandidate(
    val name: String,
    val ip: String,
    val port: Int
)

@Serializable
data class DeviceInfoResponse(
    val id: String,
    val name: String,
    val port: Int
)

@Serializable
sealed class ServerEvent {
    @Serializable
    data class PairingRequest(val deviceId: String, val deviceName: String, val pin: String) : ServerEvent()
    
    @Serializable
    data class TransferStarted(val fileId: String, val fileName: String, val size: Long) : ServerEvent()
    
    @Serializable
    data class TransferProgress(val fileId: String, val progress: Float) : ServerEvent()
    
    @Serializable
    data class TransferCompleted(val fileId: String) : ServerEvent()
    
    @Serializable
    data class TransferFailed(val fileId: String, val error: String) : ServerEvent()
}

@Serializable
data class FileTransfer(
    val id: String,
    val fileName: String,
    val size: Long,
    val progress: Float = 0f,
    val status: TransferStatus = TransferStatus.PENDING,
    val isIncoming: Boolean = true,
    val remoteDeviceName: String
)

enum class TransferStatus {
    PENDING, ONGOING, COMPLETED, FAILED
}

@Serializable
enum class DevicePermission {
    BROWSE_FILES,
    UPLOAD_FILES,
    DOWNLOAD_FILES,
    MANAGE_PERMISSIONS,
    DELETE_FILES
}

@Serializable
data class TrustedDevice(
    val id: String,
    val name: String,
    val fingerprint: String,
    val note: String,
    val blocked: Boolean,
    val lastSeen: Long,
    val permissions: Set<DevicePermission> = emptySet()
)
