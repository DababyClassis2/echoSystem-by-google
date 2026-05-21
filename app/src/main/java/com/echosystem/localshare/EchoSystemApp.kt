package com.echosystem.localshare

import android.app.Application
import com.echosystem.localshare.logging.AppLogger
import com.echosystem.localshare.notification.AppNotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoSystemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
        AppNotificationManager.initChannels(this)
    }
}

