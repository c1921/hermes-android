package com.nousresearch.hermes.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nousresearch.hermes.R
import androidx.core.view.WindowCompat

val NousBlue = Color(0xFF0053FD)
val HermesPaper = Color(0xFFFFE6CB)
val HermesAccent = Color(0xFF1540B1)
val Danger = Color(0xFFC72E4D)
val Success = Color(0xFF147D55)
val Warning = Color(0xFFB46800)

enum class HermesSkin(
    val id: String,
    val label: String,
    val description: String,
) {
    NOUS("nous", "Nous", "Glass neutrals with Nous blue accents"),
    MIDNIGHT("midnight", "Midnight", "Deep blue-violet with cool accents"),
    EMBER("ember", "Ember", "Warm crimson and bronze, forge vibes"),
    MONO("mono", "Mono", "Clean grayscale, minimal and focused"),
    CYBERPUNK("cyberpunk", "Cyberpunk", "Neon green on black, matrix terminal"),
    SLATE("slate", "Slate", "Cool slate blue, focused developer theme"),
    ;

    companion object {
        fun fromId(value: String?): HermesSkin = entries.firstOrNull { it.id == value } ?: NOUS
    }
}

internal data class HermesPalette(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val popover: Color,
    val popoverForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val midground: Color = ring,
    val destructive: Color,
    val destructiveForeground: Color,
    val sidebarBackground: Color,
    val sidebarBorder: Color,
    val userBubble: Color,
    val userBubbleBorder: Color,
)

internal fun HermesSkin.palette(dark: Boolean): HermesPalette {
    val seed = when (this) {
        HermesSkin.NOUS -> nousLight
        HermesSkin.MIDNIGHT -> midnight
        HermesSkin.EMBER -> ember
        HermesSkin.MONO -> mono
        HermesSkin.CYBERPUNK -> cyberpunk
        HermesSkin.SLATE -> slate
    }
    return when {
        this == HermesSkin.NOUS && dark -> nousDark
        this == HermesSkin.NOUS -> seed
        dark -> seed
        else -> seed.synthesiseLight()
    }
}

internal fun HermesSkin.colorScheme(dark: Boolean): ColorScheme = palette(dark).toColorScheme(dark)

private val nousLight = HermesPalette(
    background = Color(0xFFF8FAFF),
    foreground = Color(0xFF17171A),
    card = Color.White,
    cardForeground = Color(0xFF17171A),
    muted = mix(Color.White, NousBlue, 0.05f),
    mutedForeground = Color(0xFF666678),
    popover = Color.White,
    popoverForeground = Color(0xFF17171A),
    primary = NousBlue,
    primaryForeground = Color(0xFFFCFCFC),
    secondary = mix(Color.White, NousBlue, 0.07f),
    secondaryForeground = Color(0xFF242432),
    accent = mix(Color.White, NousBlue, 0.10f),
    accentForeground = Color(0xFF202030),
    border = NousBlue.copy(alpha = 0.22f),
    input = NousBlue.copy(alpha = 0.30f),
    ring = NousBlue,
    midground = NousBlue,
    destructive = Danger,
    destructiveForeground = Color.White,
    sidebarBackground = Color(0xFFF3F7FF),
    sidebarBorder = NousBlue.copy(alpha = 0.18f),
    userBubble = mix(Color.White, NousBlue, 0.06f),
    userBubbleBorder = NousBlue.copy(alpha = 0.24f),
)

private val nousDark = HermesPalette(
    background = Color(0xFF0D2F86), foreground = HermesPaper,
    card = Color(0xFF12378F), cardForeground = HermesPaper,
    muted = Color(0xFF183F9A), mutedForeground = Color(0xFFB5C7F3),
    popover = Color(0xFF123A96), popoverForeground = HermesPaper,
    primary = HermesPaper, primaryForeground = Color(0xFF0D2F86),
    secondary = Color(0xFF1B45A4), secondaryForeground = Color(0xFFE0E8FF),
    accent = HermesAccent, accentForeground = Color(0xFFF0F4FF),
    border = Color(0xFF3158AD), input = Color(0xFF0B2566), ring = HermesPaper,
    midground = NousBlue, destructive = Color(0xFFC0473A), destructiveForeground = Color(0xFFFEF2F2),
    sidebarBackground = Color(0xFF09286F), sidebarBorder = Color(0xFF234A9C),
    userBubble = Color(0xFF143B91), userBubbleBorder = Color(0xFF3A63BD),
)

private val midnight = HermesPalette(
    background = Color(0xFF08081C), foreground = Color(0xFFDDD6FF),
    card = Color(0xFF0D0D28), cardForeground = Color(0xFFDDD6FF),
    muted = Color(0xFF13133A), mutedForeground = Color(0xFF7C7AB0),
    popover = Color(0xFF0F0F2E), popoverForeground = Color(0xFFDDD6FF),
    primary = Color(0xFFDDD6FF), primaryForeground = Color(0xFF08081C),
    secondary = Color(0xFF1A1A4A), secondaryForeground = Color(0xFFC4BFF0),
    accent = Color(0xFF1A1A44), accentForeground = Color(0xFFD0C8FF),
    border = Color(0xFF1E1E52), input = Color(0xFF1E1E52), ring = Color(0xFF8B80E8),
    destructive = Color(0xFFB03060), destructiveForeground = Color(0xFFFEF2F2),
    sidebarBackground = Color(0xFF06061A), sidebarBorder = Color(0xFF12123A),
    userBubble = Color(0xFF14143A), userBubbleBorder = Color(0xFF242466),
)

private val ember = HermesPalette(
    background = Color(0xFF160800), foreground = Color(0xFFFFD8B0),
    card = Color(0xFF1E0E04), cardForeground = Color(0xFFFFD8B0),
    muted = Color(0xFF2A1408), mutedForeground = Color(0xFFAA7A56),
    popover = Color(0xFF221008), popoverForeground = Color(0xFFFFD8B0),
    primary = Color(0xFFFFD8B0), primaryForeground = Color(0xFF160800),
    secondary = Color(0xFF341800), secondaryForeground = Color(0xFFF0C090),
    accent = Color(0xFF301600), accentForeground = Color(0xFFE8C080),
    border = Color(0xFF3A1C08), input = Color(0xFF3A1C08), ring = Color(0xFFD97316),
    destructive = Color(0xFFC43010), destructiveForeground = Color(0xFFFEF2F2),
    sidebarBackground = Color(0xFF100600), sidebarBorder = Color(0xFF2A1004),
    userBubble = Color(0xFF2A1000), userBubbleBorder = Color(0xFF4A2010),
)

private val mono = HermesPalette(
    background = Color(0xFF0E0E0E), foreground = Color(0xFFEAEAEA),
    card = Color(0xFF141414), cardForeground = Color(0xFFEAEAEA),
    muted = Color(0xFF1E1E1E), mutedForeground = Color(0xFF808080),
    popover = Color(0xFF181818), popoverForeground = Color(0xFFEAEAEA),
    primary = Color(0xFFEAEAEA), primaryForeground = Color(0xFF0E0E0E),
    secondary = Color(0xFF262626), secondaryForeground = Color(0xFFC8C8C8),
    accent = Color(0xFF222222), accentForeground = Color(0xFFD8D8D8),
    border = Color(0xFF2A2A2A), input = Color(0xFF2A2A2A), ring = Color(0xFF9A9A9A),
    destructive = Color(0xFFA84040), destructiveForeground = Color(0xFFFEF2F2),
    sidebarBackground = Color(0xFF0A0A0A), sidebarBorder = Color(0xFF202020),
    userBubble = Color(0xFF1A1A1A), userBubbleBorder = Color(0xFF363636),
)

private val cyberpunk = HermesPalette(
    background = Color(0xFF000A00), foreground = Color(0xFF00FF41),
    card = Color(0xFF001200), cardForeground = Color(0xFF00FF41),
    muted = Color(0xFF001A00), mutedForeground = Color(0xFF1A8A30),
    popover = Color(0xFF001000), popoverForeground = Color(0xFF00FF41),
    primary = Color(0xFF00FF41), primaryForeground = Color(0xFF000A00),
    secondary = Color(0xFF002800), secondaryForeground = Color(0xFF00CC34),
    accent = Color(0xFF002000), accentForeground = Color(0xFF00E038),
    border = Color(0xFF003000), input = Color(0xFF003000), ring = Color(0xFF00FF41),
    destructive = Color(0xFFFF003C), destructiveForeground = Color(0xFF000A00),
    sidebarBackground = Color(0xFF000600), sidebarBorder = Color(0xFF001800),
    userBubble = Color(0xFF001400), userBubbleBorder = Color(0xFF004800),
)

private val slate = HermesPalette(
    background = Color(0xFF0D1117), foreground = Color(0xFFC9D1D9),
    card = Color(0xFF161B22), cardForeground = Color(0xFFC9D1D9),
    muted = Color(0xFF21262D), mutedForeground = Color(0xFF8B949E),
    popover = Color(0xFF1C2128), popoverForeground = Color(0xFFC9D1D9),
    primary = Color(0xFFC9D1D9), primaryForeground = Color(0xFF0D1117),
    secondary = Color(0xFF2A3038), secondaryForeground = Color(0xFFADB5BF),
    accent = Color(0xFF1E2530), accentForeground = Color(0xFFC0C8D0),
    border = Color(0xFF30363D), input = Color(0xFF30363D), ring = Color(0xFF58A6FF),
    destructive = Color(0xFFCF4848), destructiveForeground = Color(0xFFFEF2F2),
    sidebarBackground = Color(0xFF090D13), sidebarBorder = Color(0xFF1C2228),
    userBubble = Color(0xFF1E2A38), userBubbleBorder = Color(0xFF2E4060),
)

private fun HermesPalette.synthesiseLight(): HermesPalette {
    val soft = mix(Color.White, ring, 0.10f)
    val border = mix(Color(0xFFECECEF), ring, 0.14f)
    return HermesPalette(
        background = Color.White, foreground = Color(0xFF161616),
        card = Color.White, cardForeground = Color(0xFF161616),
        muted = mix(Color.White, ring, 0.06f),
        mutedForeground = mix(Color(0xFF6B6B70), ring, 0.16f),
        popover = Color.White, popoverForeground = Color(0xFF161616),
        primary = ring, primaryForeground = readableOn(ring),
        secondary = soft, secondaryForeground = mix(Color(0xFF2A2A2A), ring, 0.34f),
        accent = soft, accentForeground = mix(Color(0xFF2A2A2A), ring, 0.34f),
        border = border, input = mix(Color(0xFFE2E2E6), ring, 0.18f), ring = ring,
        midground = midground, destructive = Color(0xFFB94A3A), destructiveForeground = Color.White,
        sidebarBackground = mix(Color(0xFFFAFAFA), ring, 0.05f), sidebarBorder = border,
        userBubble = soft, userBubbleBorder = border,
    )
}

private fun HermesPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = primaryForeground,
        primaryContainer = secondary,
        onPrimaryContainer = secondaryForeground,
        secondary = accent,
        onSecondary = accentForeground,
        secondaryContainer = userBubble,
        onSecondaryContainer = foreground,
        tertiary = midground,
        onTertiary = readableOn(midground),
        tertiaryContainer = muted,
        onTertiaryContainer = mutedForeground,
        background = background,
        onBackground = foreground,
        surface = card,
        onSurface = cardForeground,
        surfaceVariant = card,
        onSurfaceVariant = mutedForeground,
        surfaceTint = primary,
        inverseSurface = foreground,
        inverseOnSurface = background,
        inversePrimary = primary,
        error = destructive,
        onError = destructiveForeground,
        errorContainer = destructive,
        onErrorContainer = destructiveForeground,
        outline = border,
        outlineVariant = input,
        scrim = foreground,
        surfaceBright = popover,
        surfaceDim = muted,
        surfaceContainer = card,
        surfaceContainerHigh = popover,
        surfaceContainerHighest = muted,
        surfaceContainerLow = card,
        surfaceContainerLowest = background,
    )
}

private fun mix(a: Color, b: Color, amount: Float): Color = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = a.alpha + (b.alpha - a.alpha) * amount,
)

private fun readableOn(color: Color): Color = if (color.luminance() > 0.58f) Color(0xFF161616) else Color.White

internal fun useDarkSystemBarIcons(background: Color): Boolean = background.luminance() > 0.5f

private val HermesDisplay = FontFamily(Font(R.font.cormorant_garamond, weight = FontWeight.Light))

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
fun HermesTheme(
    skin: HermesSkin = HermesSkin.NOUS,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = skin.colorScheme(darkTheme),
        typography = HermesTypography,
        shapes = HermesShapes,
    ) {
        val view = LocalView.current
        val darkIcons = useDarkSystemBarIcons(MaterialTheme.colorScheme.background)
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = darkIcons
                    isAppearanceLightNavigationBars = darkIcons
                }
            }
        }
        content()
    }
}
