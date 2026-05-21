package com.echosystem.localshare.core

import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer

// 1. Network Modes Supported
enum class NetworkMode {
    WIFI_DIRECT,
    HOTSPOT,
    LAN,
    WEB_DASHBOARD
}

// 2. Centralized App-wide Coordination State
sealed class CoreState {
    object Idle : CoreState()
    object Discovering : CoreState()
    data class Transferring(val activeCount: Int, val aggregateSpeedBps: Double) : CoreState()
    object WebShareActive : CoreState()
    data class Error(val message: String, val code: String) : CoreState()
    object Recovering : CoreState()
}

// 3. System-wide Core Unified Event Bus Types
sealed class CoreEvent {
    // Discovery
    data class DeviceDiscovered(val device: Device) : CoreEvent()
    data class DeviceLost(val deviceId: String) : CoreEvent()
    
    // Transfer Control
    data class TransferStarted(val transferId: String, val fileName: String, val size: Long) : CoreEvent()
    data class TransferProgress(val transferId: String, val progress: Float, val speedBps: Double) : CoreEvent()
    data class TransferCompleted(val transferId: String) : CoreEvent()
    data class TransferFailed(val transferId: String, val error: String) : CoreEvent()
    
    // Networking
    data class NetworkModeChanged(val oldMode: NetworkMode, val newMode: NetworkMode) : CoreEvent()
    
    // Web Shares
    data class WebShareStarted(val port: Int, val dashboardUrl: String) : CoreEvent()
    object WebShareStopped : CoreEvent()
}

// 4. Performance & Telemetry Snapshot
data class PerformanceSnapshot(
    val activeTransfers: Int = 0,
    val currentSpeedBps: Double = 0.0,
    val peakSpeedBps: Double = 0.0,
    val averageLatencyMs: Long = 0,
    val totalBytesTransferred: Long = 0L,
    val errorRate: Double = 0.0,
    val systemCpuLoad: Double = 0.0,
    val systemFreeRamMb: Long = 0L,
    val networkMode: NetworkMode = NetworkMode.LAN
)
