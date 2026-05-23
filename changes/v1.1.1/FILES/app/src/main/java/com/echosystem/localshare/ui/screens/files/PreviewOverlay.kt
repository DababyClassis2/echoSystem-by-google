package com.echosystem.localshare.ui.screens.files

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
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
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp) // Space for close button
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
                    .background(Color.Black.copy(alpha = 0.6f))
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
                    color = Color.White.copy(alpha = 0.7f)
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
                    offset += pan
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 50 && scale == 1f) {
                        onDismiss()
                    }
                }
            }
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun VideoPreview(file: File) {
    val context = LocalContext.current
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
                        mp.setVolume(0f, 0f) // Muted by default as per request
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16/9f)
        )
    }
}

@Composable
fun AudioPreview(file: File) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(file) {
        mediaPlayer.setDataSource(file.absolutePath)
        mediaPlayer.prepare()
        mediaPlayer.start()
        isPlaying = true
        
        onDispose {
            mediaPlayer.release()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        IconButton(
            onClick = {
                if (isPlaying) mediaPlayer.pause() else mediaPlayer.start()
                isPlaying = !isPlaying
            },
            modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(32.dp))
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary
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
        color = Color.White.copy(alpha = 0.05f),
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
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No visual preview available for this format",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
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
