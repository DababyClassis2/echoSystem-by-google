package com.echosystem.localshare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val CosmicSlate = Color(0xFF1A1C1E)
val CosmicBlue = Color(0xFFD1E4FF)
val MetallicBlue = Color(0xFF334A66)
val MetallicSurface = Color(0xFF2C3135)

private val DarkColorScheme = darkColorScheme(
    primary = CosmicBlue,
    secondary = MetallicBlue,
    tertiary = Color(0xFFACC7E4),
    background = CosmicSlate,
    surface = MetallicSurface,
    onPrimary = Color(0xFF003258),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
)

@Composable
fun EchoSystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
