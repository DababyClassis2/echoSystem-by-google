package com.echosystem.localshare.core

import android.net.Uri
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.repository.FileRepository
import com.echosystem.localshare.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    // Tracks current files and tasks queued or transferring
    private val _transferQueue = MutableStateFlow<List<FileTransfer>>(emptyList())
    val transferQueue: StateFlow<List<FileTransfer>> = _transferQueue.asStateFlow()

    // Map to maintain cancelable active coroutine execution jobs for each transfer task ID
    private val activeJobs = mutableMapOf<String, Job>()
    
    // Set containing paused transfer task references
    private val pausedTransfers = mutableSetOf<String>()

    /**
     * Enqueues a new transfer task to the central system core registry.
     */
    fun enqueueTransfer(transfer: FileTransfer, fileUri: Uri? = null, onExecute: suspend (FileTransfer, (Float, Long) -> Unit) -> Unit) {
        _transferQueue.update { it + transfer }
        AppLogger.logEvent(tag, "Enqueued file transfer task: ${transfer.fileName} (${transfer.size} bytes)")
        
        eventBus.tryEmit(CoreEvent.TransferStarted(transfer.id, transfer.fileName, transfer.size))
        
        // Spawn asynchronous concurrent worker bound to local task lifecycle
        val transferJob = scope.launch {
            try {
                updateStatus(transfer.id, TransferStatus.ONGOING)
                var lastProgressTime = System.currentTimeMillis()
                var lastBytesTransferred = 0L

                onExecute(transfer) { progress, bytesTransferredDelta ->
                    // Guard against executing paused blocks
                    if (isPaused(transfer.id)) {
                        throw CancellationException("Transfer ${transfer.id} paused by supervisor.")
                    }

                    updateProgress(transfer.id, progress)
                    
                    // Update streaming speed in tracking modules
                    val now = System.currentTimeMillis()
                    val timeDelta = (now - lastProgressTime).coerceAtLeast(1)
                    val speedBps = (bytesTransferredDelta * 1000.0) / timeDelta
                    
                    performanceTracker.recordTransferProgress(
                        activeTransfersCount = activeJobs.size,
                        speedBps = speedBps,
                        deltaBytes = bytesTransferredDelta
                    )
                    
                    eventBus.tryEmit(CoreEvent.TransferProgress(transfer.id, progress, speedBps))
                    
                    lastProgressTime = now
                    performanceTracker.recordAttempt(true)
                }

                updateStatus(transfer.id, TransferStatus.COMPLETED)
                eventBus.tryEmit(CoreEvent.TransferCompleted(transfer.id))
                AppLogger.logEvent(tag, "Successfully finalized transfer: ${transfer.fileName}")
            } catch (ce: CancellationException) {
                // Task was canceled or paused
                if (isPaused(transfer.id)) {
                    AppLogger.logEvent(tag, "Transfer ${transfer.id} (${transfer.fileName}) successfully flagged as PAUSED.")
                } else {
                    updateStatus(transfer.id, TransferStatus.FAILED)
                    eventBus.tryEmit(CoreEvent.TransferFailed(transfer.id, "Transfer was canceled by user."))
                    AppLogger.logEvent(tag, "Cancelled transfer task: ${transfer.fileName}")
                }
            } catch (t: Throwable) {
                performanceTracker.recordAttempt(false)
                val cleanError = errorHandler.reportError(t, "TransferEngineCore_Task_${transfer.id}")
                updateStatus(transfer.id, TransferStatus.FAILED)
            } finally {
                activeJobs.remove(transfer.id)
            }
        }
        
        activeJobs[transfer.id] = transferJob
    }

    /**
     * Pauses an active transfer job.
     */
    fun pauseTransfer(transferId: String) {
        pausedTransfers.add(transferId)
        activeJobs[transferId]?.cancel(CancellationException("Pause requested."))
        activeJobs.remove(transferId)
        
        _transferQueue.update { queue ->
            queue.map { if (it.id == transferId) it.copy(status = TransferStatus.PENDING) else it }
        }
    }

    /**
     * Resumes a paused transfer task.
     */
    fun resumeTransfer(transferId: String, onExecute: suspend (FileTransfer, (Float, Long) -> Unit) -> Unit) {
        pausedTransfers.remove(transferId)
        val transfer = _transferQueue.value.find { it.id == transferId }
        if (transfer != null) {
            enqueueTransfer(transfer, null, onExecute)
        }
    }

    /**
     * Cancels / aborts an active transfer task.
     */
    fun cancelTransfer(transferId: String) {
        pausedTransfers.remove(transferId)
        activeJobs[transferId]?.cancel()
        activeJobs.remove(transferId)
        
        _transferQueue.update { queue ->
            queue.map { if (it.id == transferId) it.copy(status = TransferStatus.FAILED, progress = 0f) else it }
        }
    }

    /**
     * Prioritizes a specific transfer file ahead of others in queue.
     */
    fun prioritizeTransfer(transferId: String) {
        _transferQueue.update { queue ->
            val target = queue.find { it.id == transferId } ?: return@update queue
            val filtered = queue.filter { it.id != transferId }
            listOf(target) + filtered // move task to front of buffer queue
        }
    }

    fun isPaused(transferId: String): Boolean = pausedTransfers.contains(transferId)

    private fun updateStatus(id: String, status: TransferStatus) {
        _transferQueue.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }

    private fun updateProgress(id: String, progress: Float) {
        _transferQueue.update { list ->
            list.map { if (it.id == id) it.copy(progress = progress) else it }
        }
    }
}
