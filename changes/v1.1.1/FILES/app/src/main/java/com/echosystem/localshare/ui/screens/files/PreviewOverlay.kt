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

@Composable
fun PreviewOverlay(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var dragOffsetY by remember { mutableStateOf(0f) }
        val density = LocalDensity.current
        val swipeThresholdPx = with(density) { 150.dp.toPx() }

        Box(modifier = Modifier.fillMaxSize().background(PreviewBgColor.copy(alpha = 0.95f)).pointerInput(Unit) {
            detectVerticalDragGestures(onDragEnd = { if (dragOffsetY > swipeThresholdPx || dragOffsetY < -swipeThresholdPx) onDismiss() else dragOffsetY = 0f }, onVerticalDrag = { change, dragAmount -> change.consume(); dragOffsetY += dragAmount })
        }.offset { IntOffset(0, dragOffsetY.roundToInt()) }) {
            
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()) {
                Icon(Icons.Default.Close, "Close", tint = PreviewAccentColor)
            }

            Box(modifier = Modifier.fillMaxSize().padding(top = 80.dp, bottom = 100.dp)) {
                val ext = file.extension.lowercase()
                when {
                    ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> ImagePreview(file)
                    ext in listOf("mp4", "mkv", "mov") -> VideoPreview(file)
                    ext in listOf("mp3", "wav", "m4a") -> AudioPreview(file)
                    else -> TextPreview(file)
                }
            }

            Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp).navigationBarsPadding()) {
                Text(text = file.name, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${file.length() / 1024} KB • ${file.extension.uppercase()}", style = MaterialTheme.typography.bodySmall, color = PreviewAccentColor)
            }
        }
    }
}

@Composable
fun ImagePreview(file: File) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        AsyncImage(model = file, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Composable
fun VideoPreview(file: File) {
    AndroidView(factory = { ctx -> VideoView(ctx).apply { setVideoURI(Uri.fromFile(file)); start() } }, modifier = Modifier.fillMaxSize())
}

@Composable
fun AudioPreview(file: File) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Default.MusicNote, null, tint = PreviewAccentColor, modifier = Modifier.size(120.dp))
        Text(file.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TextPreview(file: File) {
    val text = remember { try { file.readText().take(2000) } catch(e: Exception) { "Error loading" } }
    Surface(Modifier.fillMaxSize().padding(16.dp), color = Color.DarkGray) {
        LazyColumn(Modifier.padding(16.dp)) { item { Text(text, color = Color.White, style = MaterialTheme.typography.bodySmall) } }
    }
}
