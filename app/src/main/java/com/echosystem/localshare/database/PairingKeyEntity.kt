package com.echosystem.localshare.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pairing_keys")
data class PairingKeyEntity(
    @PrimaryKey val deviceId: String,
    val publicKeyBlob: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PairingKeyEntity
        if (deviceId != other.deviceId) return false
        if (!publicKeyBlob.contentEquals(other.publicKeyBlob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + publicKeyBlob.contentHashCode()
        return result
    }
}
