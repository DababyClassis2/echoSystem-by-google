package com.echosystem.localshare.core

import com.echosystem.localshare.logging.AppLogger
import java.io.IOException
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreErrorHandler @Inject constructor(
    private val eventBus: CoreEventBus
) {
    /**
     * Captures, formats, logs and maps an underlying exception to a localized diagnostic profile.
     */
    fun reportError(throwable: Throwable, contextTag: String): String {
        val userMessage = mapToUserFriendlyMessage(throwable)
        val traceMsg = "[$contextTag] Root Cause: ${throwable.javaClass.simpleName} => Message: ${throwable.message}"
        
        // Push event directly into the Logging module
        AppLogger.logEvent("CORE_ERROR_HANDLER", traceMsg)
        AppLogger.logCrash(throwable)

        // Broadcast failure to any observing view model
        eventBus.tryEmit(CoreEvent.TransferFailed("err_${System.currentTimeMillis()}", userMessage))
        
        return userMessage
    }

    /**
     * Translates low-level JVM exceptions into plain-English feedback.
     */
    fun mapToUserFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "The transfer timed out. Please check that both devices remain connected and active."
            is ConnectException -> "Unable to establish direct socket line. Ensure target host has not gone offline or closed our port."
            is FileNotFoundException -> "The requested file or document registry was not found or could not be opened."
            is IOException -> "A storage error or local transport failure occurred during writing chunks to disk."
            is SecurityException -> "Permission restricted. LocalShare requires appropriate storage read/write clearance."
            else -> throwable.localizedMessage ?: "An unexpected transmission error occurred inside the transfer engine core."
        }
    }
}
