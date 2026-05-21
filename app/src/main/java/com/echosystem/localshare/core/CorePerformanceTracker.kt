package com.echosystem.localshare.core

import android.content.Context
import android.os.SystemClock
import com.echosystem.localshare.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorePerformanceTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modeManager: NetworkModeManager
) {
    private val _snapshot = MutableStateFlow(PerformanceSnapshot())
    val snapshot: StateFlow<PerformanceSnapshot> = _snapshot.asStateFlow()

    private var totalBytes: Long = 0
    private var totalErrors: Int = 0
    private var totalAttempts: Int = 0
    private var sumLatency: Long = 0L
    private var latencyCount: Int = 0
    private var peakBps: Double = 0.0

    /**
     * Updates live active transmission speeds and adds current chunk sizes to historical byte counts.
     */
    fun recordTransferProgress(activeTransfersCount: Int, speedBps: Double, deltaBytes: Long) {
        totalBytes += deltaBytes
        if (speedBps > peakBps) {
            peakBps = speedBps
        }

        rebuildSnapshot(activeTransfersCount, speedBps)
    }

    /**
     * Records transaction latency parameters (e.g. handshake round-trips duration).
     */
    fun recordLatency(latencyMs: Long) {
        sumLatency += latencyMs
        latencyCount++
        
        rebuildSnapshot()
    }

    /**
     * Records outcome codes to determine real-time transmission errors.
     */
    fun recordAttempt(isSuccess: Boolean) {
        totalAttempts++
        if (!isSuccess) {
            totalErrors++
        }
        rebuildSnapshot()
    }

    /**
     * Builds and updates the telemetry snapshot structure.
     */
    private fun rebuildSnapshot(activeCount: Int = _snapshot.value.activeTransfers, currentSpeed: Double = _snapshot.value.currentSpeedBps) {
        val freeMemory = Runtime.getRuntime().freeMemory()
        val totalMemory = Runtime.getRuntime().totalMemory()
        val freeMemoryMb = (totalMemory - freeMemory) / (1024 * 1024)

        val errRate = if (totalAttempts > 0) {
            totalErrors.toDouble() / totalAttempts.toDouble()
        } else {
            0.0
        }

        val avgLat = if (latencyCount > 0) {
            sumLatency / latencyCount
        } else {
            0L
        }

        _snapshot.update {
            PerformanceSnapshot(
                activeTransfers = activeCount,
                currentSpeedBps = currentSpeed,
                peakSpeedBps = peakBps,
                averageLatencyMs = avgLat,
                totalBytesTransferred = totalBytes,
                errorRate = errRate,
                systemCpuLoad = 0.05, // estimated light load baseline
                systemFreeRamMb = freeMemoryMb,
                networkMode = modeManager.currentMode.value
            )
        }
    }
}
