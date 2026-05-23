package com.echosystem.localshare.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echosystem.localshare.database.DeviceEntity
import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.model.TrustedDevice
import com.echosystem.localshare.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val scope: CoroutineScope
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
        scope.launch(Dispatchers.IO) {
            deviceRepository.allDevices.collect { entities ->
                val list = entities.map { entity ->
                    val perms = entity.permissions.split(",")
                        .filter { it.isNotEmpty() }
                        .mapNotNull { 
                            runCatching { DevicePermission.valueOf(it) }.getOrNull() 
                        }.toSet()
                    TrustedDevice(
                        id = entity.deviceId,
                        name = entity.name,
                        fingerprint = generateFingerprint(entity.deviceId, entity.name),
                        note = entity.notes,
                        blocked = entity.trustStatus == "BLOCKED",
                        lastSeen = entity.lastSeen,
                        permissions = perms
                    )
                }
                _trustedDevices.value = list
                _trustedDeviceIds.value = list.filter { !it.blocked && entities.any { e -> e.deviceId == it.id && e.trustStatus == "TRUSTED" } }.map { it.id }.toSet()
            }
        }
    }

    fun isDeviceTrusted(deviceId: String): Boolean {
        if (isDeviceBlocked(deviceId)) return false
        val dev = runBlocking { deviceRepository.getDeviceById(deviceId) }
        return dev?.trustStatus == "TRUSTED"
    }

    fun isDeviceBlocked(deviceId: String): Boolean {
        val dev = runBlocking { deviceRepository.getDeviceById(deviceId) }
        return dev?.trustStatus == "BLOCKED"
    }

    fun setDeviceTrust(deviceId: String, deviceName: String, trust: Boolean) {
        scope.launch(Dispatchers.IO) {
            val existing = deviceRepository.getDeviceById(deviceId)
            val ip = existing?.ipAddress ?: "0.0.0.0"
            val status = if (trust) "TRUSTED" else "UNKNOWN"
            val updated = existing?.copy(
                name = deviceName,
                trustStatus = status,
                lastSeen = System.currentTimeMillis()
            ) ?: DeviceEntity(
                deviceId = deviceId,
                name = deviceName,
                ipAddress = ip,
                trustStatus = status,
                lastSeen = System.currentTimeMillis()
            )
            deviceRepository.insertDevice(updated)
        }
    }

    fun setDeviceBlocked(deviceId: String, blocked: Boolean) {
        scope.launch(Dispatchers.IO) {
            val existing = deviceRepository.getDeviceById(deviceId)
            if (existing != null) {
                val updated = existing.copy(
                    trustStatus = if (blocked) "BLOCKED" else "TRUSTED"
                )
                deviceRepository.insertDevice(updated)
            } else {
                val newBlock = DeviceEntity(
                    deviceId = deviceId,
                    name = "Blocked Node",
                    ipAddress = "0.0.0.0",
                    trustStatus = "BLOCKED",
                    lastSeen = System.currentTimeMillis()
                )
                deviceRepository.insertDevice(newBlock)
            }
        }
    }

    fun setDeviceNote(deviceId: String, note: String) {
        scope.launch(Dispatchers.IO) {
            val existing = deviceRepository.getDeviceById(deviceId)
            if (existing != null) {
                deviceRepository.insertDevice(existing.copy(notes = note))
            }
        }
    }
    
    fun renameDevice(deviceId: String, newName: String) {
        scope.launch(Dispatchers.IO) {
            val existing = deviceRepository.getDeviceById(deviceId)
            if (existing != null) {
                deviceRepository.insertDevice(existing.copy(name = newName))
            }
        }
    }

    fun getFingerprint(deviceId: String): String? {
        val dev = runBlocking { deviceRepository.getDeviceById(deviceId) }
        return dev?.let { generateFingerprint(it.deviceId, it.name) }
    }

    fun setDevicePermissions(deviceId: String, permissions: Set<DevicePermission>) {
        val permString = permissions.joinToString(",") { it.name }
        scope.launch(Dispatchers.IO) {
            val existing = deviceRepository.getDeviceById(deviceId)
            if (existing != null) {
                deviceRepository.insertDevice(existing.copy(permissions = permString))
            }
        }
    }

    fun hasPermission(deviceId: String, permission: DevicePermission): Boolean {
        if (isDeviceBlocked(deviceId)) return false
        val perms = getPermissions(deviceId)
        return perms.contains(permission) || perms.contains(DevicePermission.MANAGE_PERMISSIONS)
    }

    fun getPermissions(deviceId: String): Set<DevicePermission> {
        val dev = runBlocking { deviceRepository.getDeviceById(deviceId) } ?: return emptySet()
        val permString = dev.permissions
        if (permString.isEmpty() && dev.trustStatus == "TRUSTED") {
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
