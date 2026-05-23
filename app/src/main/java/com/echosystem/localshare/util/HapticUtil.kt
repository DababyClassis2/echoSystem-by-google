package com.echosystem.localshare.util

import android.content.Context
import android.os.*
import android.util.Log

/**
 * Utility for providing haptic feedback across the echoSystem application.
 * Respects system hardware availability and follows material patterns.
 */
object HapticUtil {
    private const val TAG = "HapticUtil"

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve system vibrator service", e)
            null
        }
    }

    /**
     * Provide a subtle, short vibration for standard button clicks.
     */
    fun lightTap(context: Context) {
        vibrate(context, 15)
    }

    /**
     * Provide a heavy, longer vibration to signal successful pairing or transfer.
     */
    fun success(context: Context) {
        vibrate(context, 150)
    }

    /**
     * Provide a distinct double-tap pattern for errors or failures.
     */
    fun error(context: Context) {
        // Double short vibration pattern: 0ms delay, 40ms vibrate, 60ms gap, 40ms vibrate
        vibratePattern(context, longArrayOf(0, 40, 60, 40))
    }

    private fun vibrate(context: Context, duration: Long) {
        val v = getVibrator(context) ?: return
        if (!v.hasVibrator()) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(duration)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Vibrate permission missing in manifest but expected per system configuration.")
        }
    }

    private fun vibratePattern(context: Context, pattern: LongArray) {
        val v = getVibrator(context) ?: return
        if (!v.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Vibrate permission missing for pattern feedback.")
        }
    }
}
