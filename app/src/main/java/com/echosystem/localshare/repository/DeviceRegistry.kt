package com.echosystem.localshare.repository

import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.DeviceCandidate
import com.echosystem.localshare.model.DeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRegistry @Inject constructor() {
    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val devices: StateFlow<List<Device>> = MutableStateFlow<List<Device>>(emptyList()).apply {
        // In a real implementation, we would map the map to a list
    }.asStateFlow() // Simplified for now, will update below

    private val _deviceList = MutableStateFlow<List<Device>>(emptyList())
    val deviceList: StateFlow<List<Device>> = _deviceList.asStateFlow()

    fun addCandidate(candidate: DeviceCandidate) {
        val id = candidate.ip // Simple ID for now
        _devices.update { current ->
            if (current.containsKey(id)) {
                current
            } else {
                val newDevice = Device(
                    id = id,
                    name = candidate.name,
                    ip = candidate.ip,
                    port = candidate.port
                )
                current + (id to newDevice)
            }
        }
        updateList()
    }

    fun updateDeviceStatus(id: String, status: DeviceStatus) {
        _devices.update { current ->
            current[id]?.let { device ->
                current + (id to device.copy(status = status))
            } ?: current
        }
        updateList()
    }

    private fun updateList() {
        _deviceList.value = _devices.value.values.toList()
    }

    fun getDevice(id: String): Device? = _devices.value[id]
    
    fun removeDevice(id: String) {
        _devices.update { it - id }
        updateList()
    }
}
