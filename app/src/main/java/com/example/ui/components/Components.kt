package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Device
import com.example.model.HistoryRecord
import com.example.ui.theme.*

@Composable
fun ProtocolBadge(protocol: String, modifier: Modifier = Modifier) {
    val (label, color) = when (protocol.lowercase()) {
        "ble"         -> "BLE" to ProtocolBle
        "nsd"         -> "NSD" to ProtocolNsd
        "udp"         -> "UDP" to ProtocolUdp
        "wifi_direct" -> "P2P" to ProtocolWifiDirect
        else          -> protocol.uppercase() to MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onSendClick: () -> Unit,
    onPairClick: () -> Unit,
    onUnpairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "card_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .testTag("device_card_${device.id}")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp, 
            if (device.isPaired) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) 
            else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status glowing dot representing online state
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(EchoSuccess, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (device.isPaired) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Paired & Trusted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Trusted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Spec & Protocols row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${device.ip}:${device.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    device.protocols.forEach { protocol ->
                        ProtocolBadge(protocol = protocol)
                    }
                }
            }

            // Signal strength custom indicator bar
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Signal:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Custom drawn light micro bar
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bars = 5
                    val filledBars = (device.signalStrength * bars).toInt().coerceIn(1, bars)
                    for (i in 0 until bars) {
                        val isFilled = i < filledBars
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Text(
                    text = "${(device.signalStrength * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // Action buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSendClick()
                    },
                    modifier = Modifier.weight(1.2f).height(40.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Send Files", style = MaterialTheme.typography.labelMedium)
                }

                if (device.isPaired) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUnpairClick()
                        },
                        modifier = Modifier.weight(0.8f).height(40.dp),
                        shape = PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Revoke", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPairClick()
                        },
                        modifier = Modifier.weight(0.8f).height(40.dp),
                        shape = PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Trust", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    action: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        if (action != null) {
            TextButton(
                onClick = action.second,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = action.first, 
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun RadarAnimation(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val ringAlphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing, delayMillis = index * 800),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_alpha_$index"
        )
    }
    val ringScales = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing, delayMillis = index * 800),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring_scale_$index"
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isActive) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * 0.95f

            ringScales.forEachIndexed { index, scale ->
                drawCircle(
                    color = primaryColor.copy(alpha = ringAlphas[index].value * 0.35f),
                    radius = maxRadius * scale.value,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        centerContent()
    }
}

// Custom data class to hold moving particles in active pipeline transfers
data class VisualParticle(var offsetProgress: Float, val yFactor: Float, val size: Float)

@Composable
fun TransferParticlesAnimation(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val particles = remember {
        (0..8).map { i ->
            VisualParticle(
                offsetProgress = i * 0.11f,
                yFactor = (-100..100).random() / 500f,
                size = (4..7).random().toFloat()
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "particle_progress"
    )

    Canvas(modifier = modifier) {
        particles.forEachIndexed { index, particle ->
            // compute live progress
            val offsetProgress = ((progress + index * 0.11f) % 1f)
            val px = size.width * offsetProgress
            val py = size.height * (0.5f + particle.yFactor)
            
            val alpha = when {
                offsetProgress < 0.1f -> offsetProgress / 0.1f
                offsetProgress > 0.9f -> 1f - (offsetProgress - 0.9f) / 0.1f
                else -> 1f
            }
            
            drawCircle(
                color = primaryColor.copy(alpha = alpha * 0.85f),
                radius = particle.size.dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 600f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Box(modifier = modifier.background(brush))
}

@Composable
fun DeviceCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(modifier = Modifier.size(12.dp).clip(CircleShape))
                ShimmerBox(modifier = Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(4.dp)))
            }
            ShimmerBox(modifier = Modifier.width(180.dp).height(12.dp).clip(RoundedCornerShape(4.dp)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ShimmerBox(modifier = Modifier.weight(1f).height(40.dp).clip(PillShape))
                ShimmerBox(modifier = Modifier.weight(0.7f).height(40.dp).clip(PillShape))
            }
        }
    }
}

@Composable
fun TransferHistoryItem(
    record: HistoryRecord,
    modifier: Modifier = Modifier
) {
    val directionIcon = if (record.isSent) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward
    val directionColor = if (record.isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val statusIcon = if (record.isSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel
    val statusColor = if (record.isSuccess) EchoSuccess else MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Direction circle decoration
            Surface(
                shape = CircleShape,
                color = directionColor.copy(alpha = 0.12f),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = if (record.isSent) "Sent file" else "Received file",
                    tint = directionColor,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileNameSummary,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${if (record.isSent) "To" else "From"} ${record.deviceName}  •  ${record.sizeFormatted}  •  ${record.timeString}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = statusIcon,
                contentDescription = if (record.isSuccess) "Completed" else "Failed",
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
