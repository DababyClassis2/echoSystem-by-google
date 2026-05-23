package com.echosystem.localshare.core

import android.net.Uri
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferProgress
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferEngineCore @Inject constructor(
    private val fileRepository: FileRepository,
    private val eventBus: CoreEventBus,
    private val performanceTracker: CorePerformanceTracker,
    private val errorHandler: CoreErrorHandler
) {
    private val tag = "TransferEngineCore"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _transferQueue = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferQueue: StateFlow<List<FileTransfer>> = _transferQueue.asStateFlow()

    private val _transferProgress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transferProgress: StateFlow<Map<String, TransferProgress>> = _transferProgress.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val pausedTransfers = mutableSetOf<String>()

    private val CHUNK_SIZE = 256 * 1024 // 256 KB
    private val LARGE_FILE_THRESHOLD = 10 * 1024 * 1024 // 10 MB

    fun enqueueTransfer(
        transfer: FileTransfer, 
        uri: Uri?, 
        onExecute: suspend (FileTransfer, String, (Float, Long) -> Unit) -> Unit
    ) {
        _transferQueue.update { it + transfer }
        
        val initialProgress = TransferProgress(
            transferId = transfer.id,
            fileName = transfer.fileName,
            totalBytes = transfer.size,
            transferredBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = -1L,
            status = TransferStatus.QUEUED
        )
        _transferProgress.update { it + (transfer.id to initialProgress) }
        
        eventBus.tryEmit(CoreEvent.TransferStarted(transfer.id, transfer.fileName, transfer.size))

        val job = scope.launch {
            try {
                // SHA-256 Integrity Check (Brick 2)
                val checksum = if (uri != null) calculateSha256(uri) else ""
                
                updateStatus(transfer.id, TransferStatus.TRANSFERRING)
                
                val startTime = System.currentTimeMillis()
                var lastUpdate = startTime
                var bytesTransferred = 0L

                onExecute(transfer, checksum) { progress, deltaBytes ->
                    if (isPaused(transfer.id)) throw CancellationException("Paused")
                    
                    bytesTransferred += deltaBytes
                    val now = System.currentTimeMillis()
                    
                    if (now - lastUpdate >= 500) {
                        val elapsed = (now - startTime) / 1000.0
                        val speed = if (elapsed > 0) (bytesTransferred / elapsed).toLong() else 0L
                        val remaining = transfer.size - bytesTransferred
                        val eta = if (speed > 0) remaining / speed else -1L
                        
                        _transferProgress.update { map ->
                            map + (transfer.id to (map[transfer.id] ?: initialProgress).copy(
                                transferredBytes = bytesTransferred,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                status = TransferStatus.TRANSFERRING
                            ))
                        }
                        lastUpdate = now
                    }
                    
                    performanceTracker.recordTransferProgress(activeJobs.size, 0.0, deltaBytes)
                    eventBus.tryEmit(CoreEvent.TransferProgress(transfer.id, progress, 0.0))
                }

                updateStatus(transfer.id, TransferStatus.COMPLETED)
                eventBus.tryEmit(CoreEvent.TransferCompleted(transfer.id))
            } catch (ce: CancellationException) {
                if (isPaused(transfer.id)) {
                    updateStatus(transfer.id, TransferStatus.PAUSED)
                } else {
                    updateStatus(transfer.id, TransferStatus.FAILED, "Cancelled by user")
                    eventBus.tryEmit(CoreEvent.TransferFailed(transfer.id, "User Cancellation"))
                }
            } catch (t: Throwable) {
                val cleanError = errorHandler.reportError(t, "Engine_Task_${transfer.id}")
                updateStatus(transfer.id, TransferStatus.FAILED, cleanError)
                eventBus.tryEmit(CoreEvent.TransferFailed(transfer.id, cleanError))
            } finally {
                activeJobs.remove(transfer.id)
            }
        }
        activeJobs[transfer.id] = job
    }

    private fun calculateSha256(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            fileRepository.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AppLogger.logEvent(tag, "Checksum calculation failed: ${e.message}")
            ""
        }
    }

    private fun updateStatus(id: String, status: TransferStatus, error: String? = null) {
        _transferProgress.update { map ->
            map[id]?.let {
                map + (id to it.copy(status = status, errorMessage = error))
            } ?: map
        }
        _transferQueue.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }

    fun pauseTransfer(id: String) {
        pausedTransfers.add(id)
        activeJobs[id]?.cancel(CancellationException("Paused"))
    }

    fun resumeTransfer(id: String, uri: Uri?, onExecute: suspend (FileTransfer, String, (Float, Long) -> Unit) -> Unit) {
        pausedTransfers.remove(id)
        _transferQueue.value.find { it.id == id }?.let {
            enqueueTransfer(it, uri, onExecute)
        }
    }

    fun cancelTransfer(id: String) {
        pausedTransfers.remove(id)
        activeJobs[id]?.cancel()
    }

    fun prioritizeTransfer(id: String) {
        _transferQueue.update { queue ->
            val target = queue.find { it.id == id } ?: return@update queue
            listOf(target) + queue.filter { it.id != id }
        }
    }

    private fun isPaused(id: String) = pausedTransfers.contains(id)
}
