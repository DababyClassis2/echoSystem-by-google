package com.echosystem.localshare.ui.screens.files

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

val PreviewBgColor = Color(0xFF1A1C1E)
val PreviewAccentColor = Color(0xFFD1E4FF)
val PreviewCardColor = Color(0xFF2C3135)

@Composable
fun PreviewOverlay(
    file: File,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        var dragOffsetY by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 150.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PreviewBgColor.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY > swipeThresholdPx || dragOffsetY < -swipeThresholdPx) {
                                onDismiss()
                            } else {
                                dragOffsetY = 0f
                            }
                        },
                        onDragCancel = {
                            dragOffsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount
                        }
                    )
                }
                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
        ) {
            val extension = file.extension.lowercase()

            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = PreviewAccentColor
                )
            }

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp, bottom = 100.dp) // Space for close button & bottom details
            ) {
                when {
                    extension in listOf("jpg", "jpeg", "png", "webp", "gif") -> {
                        ImagePreview(file, onDismiss)
                    }
                    extension in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp") -> {
                        VideoPreview(file)
                    }
                    extension in listOf("mp3", "wav", "m4a", "flac", "ogg") -> {
                        AudioPreview(file)
                    }
                    extension in listOf("txt", "log", "json", "xml", "csv", "md", "html", "css", "js") -> {
                        TextPreview(file)
                    }
                    else -> {
                        GenericFilePreview(file)
                    }
                }
            }

            // Bottom Info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PreviewCardColor.copy(alpha = 0.9f))
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(file.length())} • ${extension.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PreviewAccentColor
                )
            }
        }
    }
}

@Composable
fun ImagePreview(file: File, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    scale = scale.coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun VideoPreview(file: File) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(this)
                    setMediaController(mediaController)
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f) // Autoplay muted as requested
                        start()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}

@Composable
fun AudioPreview(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1f) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(file) {
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration.toFloat().coerceAtLeast(1f)
            mediaPlayer.start()
            isPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                try {
                    currentPosition = mediaPlayer.currentPosition.toFloat()
                } catch (e: Exception) {
                    // media player might have been released/stopped
                }
                delay(250)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = PreviewAccentColor,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Progress Slider
        Slider(
            value = currentPosition,
            onValueChange = {
                currentPosition = it
            },
            onValueChangeFinished = {
                mediaPlayer.seekTo(currentPosition.toInt())
            },
            valueRange = 0f..duration,
            colors = SliderDefaults.colors(
                thumbColor = PreviewAccentColor,
                activeTrackColor = PreviewAccentColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Duration Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentPosition.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatTime(duration.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                } else {
                    mediaPlayer.start()
                }
                isPlaying = !isPlaying
            },
            modifier = Modifier
                .size(64.dp)
                .background(PreviewAccentColor, RoundedCornerShape(32.dp))
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = PreviewBgColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun TextPreview(file: File) {
    val textContent = remember(file) {
        try {
            file.bufferedReader().useLines { lines ->
                lines.take(1000).joinToString("\n")
            }
        } catch (e: Exception) {
            "Error loading preview: ${e.localizedMessage}"
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = PreviewCardColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            item {
                Text(
                    text = textContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GenericFilePreview(file: File) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = PreviewAccentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Size: ${formatBytes(file.length())}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Download file via..."))
                } catch (e: Exception) {
                    // Fallback to simpler action if FileProvider fails or is not declared
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(file), "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        // Silent fallback
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = PreviewAccentColor,
                contentColor = PreviewBgColor
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Icon(imageVector = Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Download",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024.0
    val sizes = listOf("B", "KB", "MB", "GB", "TB")
    val i = kotlin.math.floor(kotlin.math.log(bytes.toDouble(), k)).toInt()
    val num = bytes.toDouble() / Math.pow(k, i.toDouble())
    return "${String.format("%.1f", num)} ${sizes[i]}"
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
