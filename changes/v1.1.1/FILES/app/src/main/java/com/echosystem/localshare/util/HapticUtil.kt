package com.echosystem.localshare.util

import android.content.Context
import android.os.*
import android.util.Log

object HapticUtil {
    private const val TAG = "HapticUtil"

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve system vibrator", e)
            null
        }
    }

    fun lightTap(context: Context) = vibrate(context, 15)
    fun success(context: Context) = vibrate(context, 150)
    fun error(context: Context) = vibratePattern(context, longArrayOf(0, 40, 60, 40))

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
        } catch (e: Exception) { }
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
        } catch (e: Exception) { }
    }
}
