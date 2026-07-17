package com.nousresearch.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NousBlue = Color(0xFF0053FD)
val PsycheBlue = Color(0xFF1540B1)
val PsycheWarm = Color(0xFFFFE6CB)
val Ink = Color(0xFF17171A)
val Danger = Color(0xFFC72E4D)
val Success = Color(0xFF147D55)
val Warning = Color(0xFFB46800)

private val LightColours = lightColorScheme(
    primary = NousBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF002B84),
    secondary = PsycheBlue,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFF),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F4FF),
    onSurfaceVariant = Color(0xFF555568),
    outline = Color(0xFFB7C8ED),
    error = Danger,
)

private val DarkColours = darkColorScheme(
    primary = PsycheWarm,
    onPrimary = Color(0xFF0D2F86),
    primaryContainer = Color(0xFF1B45A4),
    onPrimaryContainer = Color(0xFFFFE6CB),
    secondary = Color(0xFFB5C7F3),
    onSecondary = Color(0xFF0D2F86),
    background = Color(0xFF09286F),
    onBackground = PsycheWarm,
    surface = Color(0xFF0D2F86),
    onSurface = PsycheWarm,
    surfaceVariant = Color(0xFF143B91),
    onSurfaceVariant = Color(0xFFB5C7F3),
    outline = Color(0xFF3158AD),
    error = Color(0xFFFFB4AB),
)

private val HermesTypography = androidx.compose.material3.Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 31.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp),
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColours else LightColours,
        typography = HermesTypography,
        content = content,
    )
}

