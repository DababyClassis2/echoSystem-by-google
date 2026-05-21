package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int = 8080,
    val isPaired: Boolean = false,
    val protocols: List<String> = emptyList(),
    val signalStrength: Float = 1.0f,
    val osName: String = "Android"
)
