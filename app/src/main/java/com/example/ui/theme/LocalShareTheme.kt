package com.example.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LocalSharePrimary = Color(0xFF00D4FF)
val LocalShareBackground = Color(0xFF0A0A0F)
val LocalShareSurface = Color(0xFF141420)

private val DarkColorScheme = darkColorScheme(
    primary = LocalSharePrimary,
    onPrimary = Color.Black,
    secondary = Color(0xFF6366F1),
    background = LocalShareBackground,
    surface = LocalShareSurface,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun LocalShareTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
