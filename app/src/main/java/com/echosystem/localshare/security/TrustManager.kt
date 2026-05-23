package com.echosystem.localshare.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.model.TrustedDevice
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

    private val _trustedDevices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices = _trustedDevices.asStateFlow()

    init {
        loadTrustedDevices()
    }

    @Synchronized
    fun loadTrustedDevices() {
        val keys = prefs.all.keys
        val trustedIds = keys.filter { it.startsWith("trusted_") && prefs.getBoolean(it, false) }
            .map { it.removePrefix("trusted_") }
            .toSet()
        _trustedDeviceIds.value = trustedIds

        // Map all known devices in the prefs
        val allDeviceIds = keys.filter { it.startsWith("name_") }
            .map { it.removePrefix("name_") }
            .distinct()

        val list = allDeviceIds.map { deviceId ->
            val name = prefs.getString("name_$deviceId", "Unknown Device") ?: "Unknown Device"
            var fingerprint = prefs.getString("fingerprint_$deviceId", "") ?: ""
            if (fingerprint.isEmpty()) {
                fingerprint = generateFingerprint(deviceId, name)
            }
            val note = prefs.getString("note_$deviceId", "") ?: ""
            val blocked = prefs.getBoolean("blocked_$deviceId", false)
            val lastSeen = prefs.getLong("last_seen_$deviceId", System.currentTimeMillis())
            val permString = prefs.getString("perms_$deviceId", "") ?: ""
            val perms = permString.split(",")
                .filter { it.isNotEmpty() }
                .mapNotNull { 
                    runCatching { DevicePermission.valueOf(it) }.getOrNull() 
                }.toSet()
            
            TrustedDevice(deviceId, name, fingerprint, note, blocked, lastSeen, perms)
        }
        _trustedDevices.value = list
    }

    fun isDeviceTrusted(deviceId: String): Boolean {
        if (isDeviceBlocked(deviceId)) return false
        return prefs.getBoolean("trusted_$deviceId", false)
    }

    fun isDeviceBlocked(deviceId: String): Boolean {
        return prefs.getBoolean("blocked_$deviceId", false)
    }

    @Synchronized
    fun setDeviceTrust(deviceId: String, deviceName: String, trust: Boolean) {
        prefs.edit().apply {
            putBoolean("trusted_$deviceId", trust)
            putString("name_$deviceId", deviceName)
            if (trust) {
                val fingerprint = generateFingerprint(deviceId, deviceName)
                putString("fingerprint_$deviceId", fingerprint)
                putLong("last_seen_$deviceId", System.currentTimeMillis())
            } else {
                remove("fingerprint_$deviceId")
            }
        }.apply()
        loadTrustedDevices()
    }

    @Synchronized
    fun setDeviceBlocked(deviceId: String, blocked: Boolean) {
        prefs.edit().apply {
            putBoolean("blocked_$deviceId", blocked)
            if (blocked) {
                putBoolean("trusted_$deviceId", false) // Blocked removes trust
            }
        }.apply()
        loadTrustedDevices()
    }

    @Synchronized
    fun setDeviceNote(deviceId: String, note: String) {
        prefs.edit().putString("note_$deviceId", note).apply()
        loadTrustedDevices()
    }
    
    @Synchronized
    fun renameDevice(deviceId: String, newName: String) {
        prefs.edit().putString("name_$deviceId", newName).apply()
        loadTrustedDevices()
    }

    fun getFingerprint(deviceId: String): String? {
        return prefs.getString("fingerprint_$deviceId", null)
    }

    @Synchronized
    fun setDevicePermissions(deviceId: String, permissions: Set<DevicePermission>) {
        val permString = permissions.joinToString(",") { it.name }
        prefs.edit().putString("perms_$deviceId", permString).apply()
        loadTrustedDevices()
    }

    fun hasPermission(deviceId: String, permission: DevicePermission): Boolean {
        if (isDeviceBlocked(deviceId)) return false
        val perms = getPermissions(deviceId)
        return perms.contains(permission) || perms.contains(DevicePermission.MANAGE_PERMISSIONS)
    }

    fun getPermissions(deviceId: String): Set<DevicePermission> {
        val permString = prefs.getString("perms_$deviceId", "") ?: ""
        if (permString.isEmpty() && isDeviceTrusted(deviceId)) {
            // Default permissions for trusted legacy devices
            return setOf(
                DevicePermission.BROWSE_FILES,
                DevicePermission.DOWNLOAD_FILES,
                DevicePermission.UPLOAD_FILES,
                DevicePermission.DELETE_FILES
            )
        }
        return permString.split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { 
                runCatching { DevicePermission.valueOf(it) }.getOrNull() 
            }.toSet()
    }

    fun generateFingerprint(deviceId: String, deviceName: String): String {
        val raw = "FINGERPRINT-$deviceId-$deviceName-SECURE-SALT"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(raw.toByteArray())
        return bytes.joinToString("") { String.format("%02x", it) }
    }
}
