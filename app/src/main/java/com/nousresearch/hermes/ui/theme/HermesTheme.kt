package com.nousresearch.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.nousresearch.hermes.R

val NousBlue = Color(0xFF0000F2)
val HermesPaper = Color(0xFFF5F5F5)
val HermesAccent = Color(0xFFEDFF45)
val Danger = Color(0xFFC72E4D)
val Success = Color(0xFF147D55)
val Warning = Color(0xFFB46800)

private val LightColours = lightColorScheme(
    primary = NousBlue,
    onPrimary = HermesPaper,
    primaryContainer = NousBlue,
    onPrimaryContainer = HermesPaper,
    secondary = NousBlue,
    onSecondary = HermesPaper,
    tertiary = Success,
    onTertiary = Color.White,
    background = HermesPaper,
    onBackground = NousBlue,
    surface = Color.White,
    onSurface = NousBlue,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF1111B5),
    outline = NousBlue,
    outlineVariant = Color(0x660000F2),
    error = Danger,
)

private val DarkColours = darkColorScheme(
    primary = HermesPaper,
    onPrimary = NousBlue,
    primaryContainer = HermesPaper,
    onPrimaryContainer = NousBlue,
    secondary = HermesPaper,
    onSecondary = NousBlue,
    tertiary = HermesAccent,
    onTertiary = NousBlue,
    background = NousBlue,
    onBackground = HermesPaper,
    surface = NousBlue,
    onSurface = HermesPaper,
    surfaceVariant = Color(0xFF1212DC),
    onSurfaceVariant = HermesPaper,
    outline = Color(0x99F5F5F5),
    outlineVariant = Color(0x66F5F5F5),
    error = Color(0xFFFFB4AB),
)

private val HermesDisplay = FontFamily(
    Font(R.font.cormorant_garamond, weight = FontWeight.Light),
)

private val HermesMono = FontFamily(
    Font(R.font.courier_prime_regular, weight = FontWeight.Normal),
    Font(R.font.courier_prime_bold, weight = FontWeight.Bold),
)

private val HermesTypography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = HermesDisplay, fontWeight = FontWeight.Light, fontSize = 54.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = HermesDisplay, fontWeight = FontWeight.Light, fontSize = 42.sp, lineHeight = 39.sp),
    headlineMedium = TextStyle(fontFamily = HermesDisplay, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = HermesDisplay, fontWeight = FontWeight.Light, fontSize = 27.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = HermesMono, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = HermesMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = HermesMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.8.sp),
    bodyLarge = TextStyle(fontFamily = HermesMono, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = HermesMono, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = HermesMono, fontSize = 12.sp, lineHeight = 17.sp),
)

private val HermesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun HermesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColours else LightColours,
        typography = HermesTypography,
        shapes = HermesShapes,
        content = content,
    )
}
