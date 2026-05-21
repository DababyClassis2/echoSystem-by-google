package com.echosystem.localshare.logging

import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileWriter

data class PerformanceSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val cpuUsage: Double,
    val usedMemoryMb: Long,
    val totalMemoryMb: Long,
    val rxBytesSec: Long,
    val txBytesSec: Long,
    val activeTransfersCount: Int,
    val averageTransferSpeedBps: Double
)

object PerformanceMonitor {
    private val _history = MutableStateFlow<List<PerformanceSnapshot>>(emptyList())
    val history = _history.asStateFlow()

    private val _perfLogs = MutableStateFlow<List<String>>(emptyList())
    val perfLogs = _perfLogs.asStateFlow()

    private var monitorJob: Job? = null
    private var lastCpuTime = Process.getElapsedCpuTime()
    private var lastSystemTime = SystemClock.elapsedRealtime()
    private var lastRxBytes = TrafficStats.getUidRxBytes(Process.myUid()).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
    private var lastTxBytes = TrafficStats.getUidTxBytes(Process.myUid()).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }

    private var activeSpeedMeasureProvider: (() -> Pair<Int, Double>)? = null

    fun registerSpeedProvider(provider: () -> Pair<Int, Double>) {
        activeSpeedMeasureProvider = provider
    }

    fun startMonitoring(scope: CoroutineScope) {
        if (monitorJob != null) return
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val snapshot = computeSnapshot()
                _history.update { (it + snapshot).takeLast(60) } // keep last 60 points
                
                // Watchdog warnings
                val maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024)
                if (snapshot.usedMemoryMb > maxMem * 0.85) {
                    addPerfLog("WARNING: Low JVM Memory bounds! Using ${snapshot.usedMemoryMb}MB/$maxMem MB.")
                }
                if (snapshot.cpuUsage > 85.0) {
                    addPerfLog("WARNING: High CPU load registered (${String.format("%.1f", snapshot.cpuUsage)}%).")
                }
                
                delay(1000)
            }
        }
    }

    private fun addPerfLog(log: String) {
        val entry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $log"
        _perfLogs.update { (it + entry).takeLast(100) }
    }

    private fun computeSnapshot(): PerformanceSnapshot {
        // CPU
        val currentCpuTime = Process.getElapsedCpuTime()
        val currentSystemTime = SystemClock.elapsedRealtime()
        val cpuDiff = currentCpuTime - lastCpuTime
        val systemDiff = currentSystemTime - lastSystemTime
        lastCpuTime = currentCpuTime
        lastSystemTime = currentSystemTime
        
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val rawCpu = if (systemDiff > 0) {
            (cpuDiff.toDouble() / systemDiff.toDouble() / cores.toDouble()) * 100.0
        } else {
            0.0
        }
        val clampedCpu = rawCpu.coerceIn(0.0, 100.0)

        // Memory
        val totalMem = Runtime.getRuntime().totalMemory()
        val freeMem = Runtime.getRuntime().freeMemory()
        val usedMem = totalMem - freeMem
        
        // Traffic
        val currentRx = TrafficStats.getUidRxBytes(Process.myUid()).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid()).let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
        
        val rxDiff = if (lastRxBytes > 0 && currentRx >= lastRxBytes) currentRx - lastRxBytes else 0L
        val txDiff = if (lastTxBytes > 0 && currentTx >= lastTxBytes) currentTx - lastTxBytes else 0L
        
        lastRxBytes = currentRx
        lastTxBytes = currentTx

        // Active Speed transfers from context provider
        val speedPair = activeSpeedMeasureProvider?.invoke() ?: Pair(0, 0.0)

        return PerformanceSnapshot(
            cpuUsage = clampedCpu,
            usedMemoryMb = usedMem / (1024 * 1024),
            totalMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024),
            rxBytesSec = rxDiff,
            txBytesSec = txDiff,
            activeTransfersCount = speedPair.first,
            averageTransferSpeedBps = speedPair.second
        )
    }

    fun exportPerfLogs(context: Context): File? {
        try {
            val file = File(context.cacheDir, "performance_watchdog_logs.txt")
            FileWriter(file).use { writer ->
                writer.write("=================== LOCALSHARE PERFORMANCE MONITOR BUNDLE ===================\n")
                writer.write("Exported at: ${java.util.Date()}\n\n")
                writer.write("--- SYSTEM LIVE METRIC HISTORY ---\n")
                _history.value.forEach { snap ->
                    writer.write("[TI: ${snap.timestamp}] CPU: ${String.format("%.1f", snap.cpuUsage)}% | RAM: ${snap.usedMemoryMb}/${snap.totalMemoryMb} MB | RX: ${snap.rxBytesSec} B/s | TX: ${snap.txBytesSec} B/s | Active transfers: ${snap.activeTransfersCount} | Dynamic speed: ${snap.averageTransferSpeedBps} Bps\n")
                }
                writer.write("\n--- RECENT PERFORMANCE WARNING EVENTS ---\n")
                if (_perfLogs.value.isEmpty()) {
                    writer.write("No low memory or CPU spike warnings recorded.\n")
                } else {
                    _perfLogs.value.forEach { entry ->
                        writer.write("$entry\n")
                    }
                }
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun clearPerfLogs() {
        _perfLogs.value = emptyList()
    }
}
