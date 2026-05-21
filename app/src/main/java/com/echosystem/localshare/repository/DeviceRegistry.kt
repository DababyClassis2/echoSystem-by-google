package com.echosystem.localshare.repository

import com.echosystem.localshare.model.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRegistry @Inject constructor() {
    private val devicesMap = ConcurrentHashMap<String, Device>()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()

    fun addOrUpdate(device: Device) {
        devicesMap[device.id] = device
        _devices.value = devicesMap.values.toList()
    }

    fun remove(deviceId: String) {
        devicesMap.remove(deviceId)
        _devices.value = devicesMap.values.toList()
    }

    fun getDevice(deviceId: String): Device? = devicesMap[deviceId]
    
    fun clear() {
        devicesMap.clear()
        _devices.value = emptyList()
    }
}
