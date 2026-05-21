package com.echosystem.localshare.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

private val DarkColorScheme = darkColorScheme(
    primary = EchoPrimaryLight,
    onPrimary = OnEchoPrimaryDark,
    primaryContainer = EchoPrimaryContainerDark,
    onPrimaryContainer = OnEchoPrimaryContainerDark,
    secondary = EchoSecondaryLight,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = EchoSecondaryContainerDark,
    onSecondaryContainer = EchoSecondaryLight,
    tertiary = EchoTertiaryLight,
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = EchoTertiaryLight,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

private val CyberpunkColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    onPrimary = Color(0xFF0A0516),
    primaryContainer = Color(0xFF2E0854),
    onPrimaryContainer = Color(0xFF00F0FF),
    secondary = Color(0xFFFF007F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF3F0A6B),
    onSecondaryContainer = Color(0xFFFF007F),
    background = Color(0xFF0A0413),
    onBackground = Color(0xFFF1EAFA),
    surface = Color(0xFF130A24),
    onSurface = Color(0xFFFDF9FF),
    surfaceVariant = Color(0xFF22113A),
    onSurfaceVariant = Color(0xFFE5D5FC),
    outline = Color(0xFFFF007F),
    outlineVariant = Color(0xFF3B1561)
)

private val SolarOledColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1C1C1C),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE5E5EA),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E)
)

private val EmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color(0xFF064E3B),
    primaryContainer = Color(0xFF062F24),
    onPrimaryContainer = Color(0xFF34D399),
    secondary = Color(0xFF059669),
    onSecondary = Color(0xFFECFDF5),
    secondaryContainer = Color(0xFF065F46),
    onSecondaryContainer = Color(0xFF6EE7B7),
    background = Color(0xFF021E14),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF032A1C),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF043B27),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0xFF10B981),
    outlineVariant = Color(0xFF064E3B)
)

private val LightColorScheme = lightColorScheme(
    primary = EchoPrimary,
    onPrimary = OnEchoPrimary,
    primaryContainer = EchoPrimaryContainer,
    onPrimaryContainer = OnEchoPrimaryContainer,
    secondary = EchoSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = EchoSecondaryContainer,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = EchoTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun MyApplicationTheme(
    themeId: Int = 2, // Default to 2 (Dark Cosmic) as requested by user
    // Dynamic color support is disabled here to preserve the bespoke EchoSystem visual style
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeId) {
        1 -> LightColorScheme
        2 -> DarkColorScheme
        3 -> CyberpunkColorScheme
        4 -> SolarOledColorScheme
        5 -> EmeraldColorScheme
        else -> DarkColorScheme // default is Dark Cosmic
    }

    val isDark = themeId != 1 // True for all except Light Champagne

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = LocalShareShapes,
        content = content
    )
}
