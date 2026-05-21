package com.example.model

data class DeviceCandidate(
    val ip: String,
    val port: Int,
    val source: DiscoverySource
)

enum class DiscoverySource {
    NSD, UDP, ARP, WIFI_DIRECT, BLE
}
