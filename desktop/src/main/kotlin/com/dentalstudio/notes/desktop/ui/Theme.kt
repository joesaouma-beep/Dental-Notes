package com.dentalstudio.notes.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// The same palette as the Android app, so a clinician using both sees one product.
val Mint10 = Color(0xFF00201B)
val Mint30 = Color(0xFF00695A)
val Mint40 = Color(0xFF00897B)
val Mint50 = Color(0xFF0FBFA0)
val Mint60 = Color(0xFF2ED7B6)
val Mint90 = Color(0xFFB6F2E5)
val Mint95 = Color(0xFFDCFBF3)

val Violet30 = Color(0xFF3A2A8C)
val Violet50 = Color(0xFF7C5CFC)
val Violet90 = Color(0xFFE4DDFF)

val Coral40 = Color(0xFFD64545)
val Coral50 = Color(0xFFFF6B6B)
val Coral90 = Color(0xFFFFDAD6)

val Amber50 = Color(0xFFFFB020)

val Ink = Color(0xFF0E1B1F)
val InkMuted = Color(0xFF4B5F66)
val Cloud = Color(0xFFF6FAFB)
val CloudAlt = Color(0xFFE7F1F2)
val Line = Color(0xFFD5E3E5)

val NightBg = Color(0xFF0A1316)
val NightSurface = Color(0xFF10201F)
val NightSurfaceAlt = Color(0xFF17302E)
val NightLine = Color(0xFF23423F)
val NightText = Color(0xFFE3F2EF)
val NightTextMuted = Color(0xFF9DB5B2)

private val LightColors = lightColorScheme(
    primary = Mint40, onPrimary = Color.White,
    primaryContainer = Mint90, onPrimaryContainer = Mint10,
    secondary = Violet50, onSecondary = Color.White,
    secondaryContainer = Violet90, onSecondaryContainer = Violet30,
    tertiary = Coral50, onTertiary = Color.White,
    background = Cloud, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = CloudAlt, onSurfaceVariant = InkMuted,
    outline = Line, outlineVariant = Line,
    error = Coral40, onError = Color.White,
    errorContainer = Coral90, onErrorContainer = Coral40,
)

private val DarkColors = darkColorScheme(
    primary = Mint60, onPrimary = Mint10,
    primaryContainer = Mint30, onPrimaryContainer = Mint95,
    secondary = Violet50, onSecondary = Color.White,
    secondaryContainer = Violet30, onSecondaryContainer = Violet90,
    tertiary = Coral50, onTertiary = Color.White,
    background = NightBg, onBackground = NightText,
    surface = NightSurface, onSurface = NightText,
    surfaceVariant = NightSurfaceAlt, onSurfaceVariant = NightTextMuted,
    outline = NightLine, outlineVariant = NightLine,
    error = Coral50, onError = Color.White,
    errorContainer = Coral40, onErrorContainer = Coral90,
)

object AppBrush {
    val hero: Brush get() = Brush.linearGradient(listOf(Mint50, Color(0xFF19A7C7), Violet50))
    val record: Brush get() = Brush.linearGradient(listOf(Mint60, Mint40))
    val recording: Brush get() = Brush.linearGradient(listOf(Coral50, Color(0xFFE0447D)))
    val learn: Brush get() = Brush.linearGradient(listOf(Violet50, Color(0xFFB06CFF)))
}

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

@Composable
fun DentalNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
