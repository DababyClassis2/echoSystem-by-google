package com.echosystem.localshare.repository

import com.echosystem.localshare.database.DeviceDao
import com.echosystem.localshare.database.DeviceEntity
import com.echosystem.localshare.database.PairingKeyDao
import com.echosystem.localshare.database.PairingKeyEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val pairingKeyDao: PairingKeyDao
) {
    val allDevices: Flow<List<DeviceEntity>> = deviceDao.getAllDevices()

    suspend fun getDeviceById(deviceId: String): DeviceEntity? = deviceDao.getDeviceById(deviceId)

    suspend fun insertDevice(device: DeviceEntity) = deviceDao.insertDevice(device)

    suspend fun updateDevice(device: DeviceEntity) = deviceDao.updateDevice(device)

    suspend fun deleteDevice(device: DeviceEntity) = deviceDao.deleteDevice(device)

    suspend fun deleteDeviceById(deviceId: String) = deviceDao.deleteDeviceById(deviceId)

    suspend fun getPairingKey(deviceId: String): PairingKeyEntity? = pairingKeyDao.getPairingKey(deviceId)

    suspend fun insertPairingKey(pairingKey: PairingKeyEntity) = pairingKeyDao.insertPairingKey(pairingKey)

    suspend fun deletePairingKeyById(deviceId: String) = pairingKeyDao.deletePairingKeyById(deviceId)
}
