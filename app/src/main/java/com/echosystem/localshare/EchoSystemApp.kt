package com.echosystem.localshare

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EchoSystemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("EchoSystem", "EchoSystemApp initialized. Hilt should be active.")
        
        // Global crash handler for better debugging in Logcat
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("EchoSystemCrash", "FATAL EXCEPTION in thread ${thread.name}", throwable)
            // Still let the system handle it normally
            System.exit(1)
        }
    }
}
