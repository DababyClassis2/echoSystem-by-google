package com.echosystem.localshare.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PairingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_pairing_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private var currentPin: String? = null

    fun generatePin(): String {
        val pin = (100000..999999).random().toString()
        currentPin = pin
        return pin
    }

    fun verifyPin(pin: String): Boolean {
        return pin == currentPin
    }

    fun markAsPaired(deviceId: String) {
        prefs.edit().putBoolean("paired_$deviceId", true).apply()
    }

    fun isPaired(deviceId: String): Boolean {
        return prefs.getBoolean("paired_$deviceId", false)
    }

    fun revokePairing(deviceId: String) {
        prefs.edit().remove("paired_$deviceId").apply()
    }

    fun getDeviceNodeName(): String {
        return android.os.Build.MODEL
    }

    fun getLocalIp(): String {
        try {
            val interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        if (!sAddr.contains(":")) { // IPv4 checking
                            return sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
