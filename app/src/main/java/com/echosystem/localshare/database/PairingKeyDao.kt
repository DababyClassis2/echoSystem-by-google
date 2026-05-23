package com.echosystem.localshare.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PairingKeyDao {
    @Query("SELECT * FROM pairing_keys WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getPairingKey(deviceId: String): PairingKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairingKey(pairingKey: PairingKeyEntity)

    @Query("DELETE FROM pairing_keys WHERE deviceId = :deviceId")
    suspend fun deletePairingKeyById(deviceId: String)
}
