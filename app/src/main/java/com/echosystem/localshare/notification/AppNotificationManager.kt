package com.echosystem.localshare.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echosystem.localshare.logging.AppLogger

object AppNotificationManager {
    private const val TAG = "AppNotificationManager"
    const val CHANNEL_TRANSFERS = "transfers_channel"
    const val CHANNEL_DISCOVERY = "discovery_channel"
    const val CHANNEL_ALERT = "alert_channel"
    
    // Notification ID tracking
    private var transferNotificationId = 1000
    private var eventNotificationId = 2000

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val transfersChannel = NotificationChannel(
                CHANNEL_TRANSFERS,
                "File Transmissions Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time status of incoming & outgoing files."
            }

            val discoveryChannel = NotificationChannel(
                CHANNEL_DISCOVERY,
                "Local Network Discoveries",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a nearby sharing device is ready or found."
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "System Alerts & Web Share",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts such as failed transfer sessions and Web Share states."
            }

            manager.createNotificationChannel(transfersChannel)
            manager.createNotificationChannel(discoveryChannel)
            manager.createNotificationChannel(alertChannel)
            AppLogger.logEvent(TAG, "Notification Channels initialized successfully.")
        }
    }

    fun notifyFileReceived(context: Context, fileName: String, formattedSize: String) {
        val id = ++transferNotificationId
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("File Received Successfully")
            .setContentText("$fileName ($formattedSize)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            // Verify permission check if Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(id, builder.build())
                }
            } else {
                manager.notify(id, builder.build())
            }
            AppLogger.logEvent(TAG, "Notified user: Received $fileName")
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed showing notification: ${e.message}")
        }
    }

    fun notifyFileSent(context: Context, fileName: String, sizeText: String) {
        val id = ++transferNotificationId
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSFERS)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("File Transmitted Successfully")
            .setContentText("$fileName ($sizeText)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(id, builder.build())
                }
            } else {
                manager.notify(id, builder.build())
            }
            AppLogger.logEvent(TAG, "Notified user: Sent $fileName")
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed showing notification: ${e.message}")
        }
    }

    fun notifyTransferFailed(context: Context, errorReason: String) {
        val id = ++eventNotificationId
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Local Share Transmission Failed")
            .setContentText(errorReason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(id, builder.build())
                }
            } else {
                manager.notify(id, builder.build())
            }
            AppLogger.logEvent(TAG, "Notified failure: $errorReason")
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed showing error notification: ${e.message}")
        }
    }

    fun notifyDeviceDiscovered(context: Context, deviceName: String, ipAddress: String) {
        val id = ++eventNotificationId
        val builder = NotificationCompat.Builder(context, CHANNEL_DISCOVERY)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Nearby Share Host Discovered")
            .setContentText("$deviceName online at $ipAddress")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(id, builder.build())
                }
            } else {
                manager.notify(id, builder.build())
            }
            AppLogger.logEvent(TAG, "Notified discovered device: $deviceName")
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed showing peer info notification: ${e.message}")
        }
    }

    fun notifyWebShareActive(context: Context, url: String) {
        val id = 5001 // Sticky/Persistent system status
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("Web Portal Gateway Active")
            .setContentText("Access: $url")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true) // Persistent until web sharing is disabled or app closes
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Portal Sharing", null) // Optional action

        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(id, builder.build())
                }
            } else {
                manager.notify(id, builder.build())
            }
            AppLogger.logEvent(TAG, "Notified Web Share portal link details: $url")
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed showing web sharing info banner: ${e.message}")
        }
    }

    fun clearWebShareActive(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(5001)
        } catch (e: Exception) {
            AppLogger.logEvent(TAG, "Failed clearing persistent notification banner.")
        }
    }
}
