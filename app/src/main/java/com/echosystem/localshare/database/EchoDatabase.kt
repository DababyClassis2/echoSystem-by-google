package com.echosystem.localshare.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DeviceEntity::class, TransferHistoryEntity::class, PairingKeyEntity::class], version = 1, exportSchema = false)
abstract class EchoDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun transferHistoryDao(): TransferHistoryDao
    abstract fun pairingKeyDao(): PairingKeyDao
}
