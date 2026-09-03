package com.dentalstudio.notes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Mint40,
    onPrimary = Color.White,
    primaryContainer = Mint90,
    onPrimaryContainer = Mint10,
    secondary = Violet50,
    onSecondary = Color.White,
    secondaryContainer = Violet90,
    onSecondaryContainer = Violet30,
    tertiary = Coral50,
    onTertiary = Color.White,
    tertiaryContainer = Coral90,
    onTertiaryContainer = Coral40,
    background = Cloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CloudAlt,
    onSurfaceVariant = InkMuted,
    outline = Line,
    outlineVariant = Line,
    error = Coral40,
    onError = Color.White,
    errorContainer = Coral90,
    onErrorContainer = Coral40,
)

private val DarkColors = darkColorScheme(
    primary = Mint60,
    onPrimary = Mint10,
    primaryContainer = Mint30,
    onPrimaryContainer = Mint95,
    secondary = Violet50,
    onSecondary = Color.White,
    secondaryContainer = Violet30,
    onSecondaryContainer = Violet90,
    tertiary = Coral50,
    onTertiary = Color.White,
    tertiaryContainer = Coral40,
    onTertiaryContainer = Coral90,
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightTextMuted,
    outline = NightLine,
    outlineVariant = NightLine,
    error = Coral50,
    onError = Color.White,
    errorContainer = Coral40,
    onErrorContainer = Coral90,
)

/** The signature gradient used for headers and the record button. */
object AppBrush {
    val hero: Brush
        get() = Brush.linearGradient(listOf(Mint50, Color(0xFF19A7C7), Violet50))

    val record: Brush
        get() = Brush.linearGradient(listOf(Mint60, Mint40))

    val recording: Brush
        get() = Brush.linearGradient(listOf(Coral50, Color(0xFFE0447D)))

    val learn: Brush
        get() = Brush.linearGradient(listOf(Violet50, Color(0xFFB06CFF)))
}

@Composable
fun DentalNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
