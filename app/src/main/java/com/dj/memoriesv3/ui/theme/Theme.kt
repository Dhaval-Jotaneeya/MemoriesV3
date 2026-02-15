package com.dj.memoriesv3.ui.theme

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

// ── Custom Rich Dark Color Palette ──────────────────────────────────────────
private val MemoriesDarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),          // Soft periwinkle blue
    onPrimary = Color(0xFF002B75),
    primaryContainer = Color(0xFF1B4199),
    onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFFBBC6E4),         // Muted blue-grey
    onSecondary = Color(0xFF263048),
    secondaryContainer = Color(0xFF3C4660),
    onSecondaryContainer = Color(0xFFD7E2FF),
    tertiary = Color(0xFFDDBCE0),          // Soft lavender
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),        // Deep dark background
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF1D2027),    // Slightly elevated surface
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFB8C3FF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44464F),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    inversePrimary = Color(0xFF3558B2),
    surfaceBright = Color(0xFF37393E),
    surfaceDim = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32343A),
    surfaceContainerLow = Color(0xFF1B1B22),
    surfaceContainerLowest = Color(0xFF0C0E13),
)

// ── Custom Rich Light Color Palette ─────────────────────────────────────────
private val MemoriesLightColors = lightColorScheme(
    primary = Color(0xFF3558B2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF001849),
    secondary = Color(0xFF535E78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E2FF),
    onSecondaryContainer = Color(0xFF101C33),
    tertiary = Color(0xFF6F5575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF28132F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF3558B2),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC5C5D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFB8C3FF),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

@Composable
fun MemoriesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MemoriesDarkColors
        else -> MemoriesLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
