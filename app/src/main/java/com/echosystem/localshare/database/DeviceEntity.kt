package com.echosystem.localshare.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val ipAddress: String,
    val trustStatus: String, // TRUSTED, BLOCKED, PENDING, UNKNOWN
    val lastSeen: Long,
    val notes: String = "",
    val permissions: String = "[]" // JSON representation of permissions/capabilities
)
