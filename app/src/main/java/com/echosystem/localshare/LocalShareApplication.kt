package com.echosystem.localshare

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LocalShareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("EchoSystem", "LocalShareApplication initialized.")
        
        // Global crash handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("EchoSystemCrash", "FATAL EXCEPTION in thread ${thread.name}", throwable)
            System.exit(1)
        }
    }
}
