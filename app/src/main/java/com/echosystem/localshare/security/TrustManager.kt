package com.echosystem.localshare.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_trust_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _trustedDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val trustedDeviceIds = _trustedDeviceIds.asStateFlow()

    init {
        loadTrustedDevices()
    }

    private fun loadTrustedDevices() {
        val keys = prefs.all.keys
        val trustedIds = keys.filter { it.startsWith("trusted_") && prefs.getBoolean(it, false) }
            .map { it.removePrefix("trusted_") }
            .toSet()
        _trustedDeviceIds.value = trustedIds
    }

    fun isDeviceTrusted(deviceId: String): Boolean {
        return prefs.getBoolean("trusted_$deviceId", false)
    }

    fun setDeviceTrust(deviceId: String, deviceName: String, trust: Boolean) {
        prefs.edit().putBoolean("trusted_$deviceId", trust).apply()
        if (trust) {
            val fingerprint = generateFingerprint(deviceId, deviceName)
            prefs.edit().putString("fingerprint_$deviceId", fingerprint).apply()
        } else {
            prefs.edit().remove("fingerprint_$deviceId").apply()
        }
        loadTrustedDevices()
    }

    fun getFingerprint(deviceId: String): String? {
        return prefs.getString("fingerprint_$deviceId", null)
    }

    fun generateFingerprint(deviceId: String, deviceName: String): String {
        val raw = "FINGERPRINT-$deviceId-$deviceName-SECURE-SALT"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray())
        return bytes.joinToString("") { String.format("%02x", it) }
    }
}
