package com.echosystem.localshare.ui.screens.devices

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.DeviceStatus

import androidx.compose.ui.platform.LocalContext
import com.echosystem.localshare.util.HapticUtil
import kotlinx.coroutines.flow.filter

@Composable
fun DeviceCard(
    device: Device,
    isTrusted: Boolean,
    pairingResults: kotlinx.coroutines.flow.Flow<com.echosystem.localshare.viewmodel.EchoViewModel.PairingResult>? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pulseColor by remember { mutableStateOf(Color.Transparent) }
    val animatedBorderColor by animateColorAsState(
        targetValue = pulseColor,
        animationSpec = tween(600),
        finishedListener = { pulseColor = Color.Transparent },
        label = "pulse_animation"
    )

    LaunchedEffect(pairingResults) {
        pairingResults?.filter { it.deviceId == device.id }?.collect { result ->
            if (result.success) {
                pulseColor = Color(0xFF4CAF50) // Pulse Green
                HapticUtil.success(context)
            } else {
                pulseColor = Color.Red // Pulse Red
                HapticUtil.error(context)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { 
                HapticUtil.lightTap(context)
                onClick() 
            }
            .then(
                if (pulseColor != Color.Transparent) {
                    Modifier.border(2.dp, animatedBorderColor, MaterialTheme.shapes.medium)
                } else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Avatar / Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.status == DeviceStatus.CONNECTED) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        device.name.contains("Phone", true) -> Icons.Default.Smartphone
                        device.name.contains("Laptop", true) -> Icons.Default.Laptop
                        device.name.contains("Desktop", true) -> Icons.Default.Computer
                        device.name.contains("Portal", true) -> Icons.Default.Web
                        else -> Icons.Default.Devices
                    },
                    contentDescription = null,
                    tint = if (device.status == DeviceStatus.CONNECTED) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isTrusted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Trusted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${device.ip}:${device.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status Indicator
            StatusIndicator(status = device.status)
        }
    }
}

@Composable
fun StatusIndicator(status: DeviceStatus) {
    val color = when (status) {
        DeviceStatus.CONNECTED -> MaterialTheme.colorScheme.primary
        DeviceStatus.AVAILABLE -> Color(0xFF4CAF50) // Material Green
        DeviceStatus.BUSY -> MaterialTheme.colorScheme.error
        DeviceStatus.PAIRING -> MaterialTheme.colorScheme.tertiary
        DeviceStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
    }

    val text = when (status) {
        DeviceStatus.CONNECTED -> "Online"
        DeviceStatus.AVAILABLE -> "Active"
        DeviceStatus.BUSY -> "Busy"
        DeviceStatus.PAIRING -> "Pairing"
        DeviceStatus.DISCONNECTED -> "Offline"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
