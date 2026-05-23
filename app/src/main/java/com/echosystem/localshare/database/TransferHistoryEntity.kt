package com.echosystem.localshare.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val fileName: String,
    val fileSize: Long,
    val direction: String, // SENT, RECEIVED
    val timestamp: Long,
    val status: String // SUCCESS, FAILED, CANCELLED
)
