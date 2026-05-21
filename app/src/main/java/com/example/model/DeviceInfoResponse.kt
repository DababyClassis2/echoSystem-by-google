package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoResponse(
    val id: String,
    val name: String,
    val port: Int,
    val protocols: List<String> = emptyList()
)
