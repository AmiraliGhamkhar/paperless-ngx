package studio.vela.art.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.vela.art.data.settings.AccentChoice
import studio.vela.art.data.settings.AppSettings
import studio.vela.art.data.settings.CornerStyle
import studio.vela.art.data.settings.DensityLevel
import studio.vela.art.data.settings.ThemeMode

/**
 * Central design tokens. Nothing in the UI hard-codes a colour, radius or spacing value; every
 * screen reads them from [VelaTheme] so themes, density and corner style are globally coherent.
 */
@Immutable
data class VelaColors(
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentMuted: Color,
    val violet: Color,
    val cyan: Color,
    val pink: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val canvasGround: Color,
    val isDark: Boolean,
)

@Immutable
data class VelaDimens(
    val gap: Dp,
    val gapSmall: Dp,
    val gapLarge: Dp,
    val pagePadding: Dp,
    val cardPadding: Dp,
    val cornerSmall: Dp,
    val cornerMedium: Dp,
    val cornerLarge: Dp,
    val cornerXLarge: Dp,
    val touchTarget: Dp,
    val iconSize: Dp,
)

private val LimeAccent = Color(0xFFC7F24E)
private val VioletAccent = Color(0xFF8B6BFF)
private val CyanAccent = Color(0xFF37E0D8)
private val PinkAccent = Color(0xFFFF5FA2)

private fun accentFor(settings: AppSettings): Color = when (settings.accent) {
    AccentChoice.LIME -> LimeAccent
    AccentChoice.VIOLET -> VioletAccent
    AccentChoice.CYAN -> CyanAccent
    AccentChoice.PINK -> PinkAccent
    AccentChoice.CUSTOM -> Color(settings.customAccentArgb)
}

private fun onAccentFor(accent: Color): Color =
    if (accent.luminance() > 0.55f) Color(0xFF14121B) else Color(0xFFF7F5FF)

private fun darkColors(accent: Color, amoled: Boolean): VelaColors = VelaColors(
    ground = if (amoled) Color(0xFF000000) else Color(0xFF0B0A10),
    surface = if (amoled) Color(0xFF0A0A0C) else Color(0xFF18161F),
    surface2 = if (amoled) Color(0xFF121216) else Color(0xFF24212D),
    surface3 = if (amoled) Color(0xFF1B1B21) else Color(0xFF2F2B3B),
    border = if (amoled) Color(0xFF26262E) else Color(0xFF3A3646),
    textPrimary = Color(0xFFEEEAF6),
    textSecondary = Color(0xFFA6A0B8),
    accent = accent,
    onAccent = onAccentFor(accent),
    accentMuted = accent.copy(alpha = 0.16f),
    violet = Color(0xFF311B6B),
    cyan = CyanAccent,
    pink = PinkAccent,
    success = Color(0xFF7BE495),
    warning = Color(0xFFF2C14E),
    error = Color(0xFFFF8A80),
    canvasGround = Color(0xFF14121B),
    isDark = true,
)

private fun lightColors(accent: Color): VelaColors = VelaColors(
    ground = Color(0xFFF7F5FB),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFECF6),
    surface3 = Color(0xFFE5E0F0),
    border = Color(0xFFD8D2E6),
    textPrimary = Color(0xFF17141F),
    textSecondary = Color(0xFF5B5568),
    accent = accent,
    onAccent = onAccentFor(accent),
    accentMuted = accent.copy(alpha = 0.22f),
    violet = Color(0xFF6B4BD6),
    cyan = Color(0xFF12A9A2),
    pink = Color(0xFFD93C7F),
    success = Color(0xFF2E9E52),
    warning = Color(0xFFB5821B),
    error = Color(0xFFC0453A),
    canvasGround = Color(0xFFE9E5F2),
    isDark = false,
)

private fun dimensFor(
    density: DensityLevel,
    cornerStyle: CornerStyle,
    fontScale: Float,
): Pair<VelaDimens, Shapes> {
    val gap = when (density) {
        DensityLevel.COMPACT -> 6.dp
        DensityLevel.COMFORTABLE -> 9.dp
        DensityLevel.SPACIOUS -> 14.dp
    }
    val small = when (cornerStyle) {
        CornerStyle.ROUNDED -> 10.dp
        CornerStyle.SOFT -> 12.dp
        CornerStyle.SHARP -> 6.dp
    }
    val medium = when (cornerStyle) {
        CornerStyle.ROUNDED -> 18.dp
        CornerStyle.SOFT -> 22.dp
        CornerStyle.SHARP -> 10.dp
    }
    val large = when (cornerStyle) {
        CornerStyle.ROUNDED -> 26.dp
        CornerStyle.SOFT -> 30.dp
        CornerStyle.SHARP -> 14.dp
    }
    val xlarge = when (cornerStyle) {
        CornerStyle.ROUNDED -> 32.dp
        CornerStyle.SOFT -> 36.dp
        CornerStyle.SHARP -> 18.dp
    }
    val dimens = VelaDimens(
        gap = gap,
        gapSmall = gap / 2,
        gapLarge = gap * 2,
        pagePadding = if (density == DensityLevel.SPACIOUS) 20.dp else 16.dp,
        cardPadding = if (density == DensityLevel.COMPACT) 12.dp else 16.dp,
        cornerSmall = small,
        cornerMedium = medium,
        cornerLarge = large,
        cornerXLarge = xlarge,
        touchTarget = 48.dp,
        iconSize = 22.dp,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(small / 2),
        small = RoundedCornerShape(small),
        medium = RoundedCornerShape(medium),
        large = RoundedCornerShape(large),
        extraLarge = RoundedCornerShape(xlarge),
    )
    return dimens to shapes
}

private fun typographyFor(scale: Float): Typography {
    fun size(sp: Float): TextUnit = (sp * scale).sp
    return Typography(
        displaySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = size(34f),
            lineHeight = size(40f),
            letterSpacing = (-0.5).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(26f),
            lineHeight = size(32f),
            letterSpacing = (-0.3).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(21f),
            lineHeight = size(27f),
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(18f),
            lineHeight = size(24f),
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(15f),
            lineHeight = size(20f),
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(15f),
            lineHeight = size(21f),
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(13.5f),
            lineHeight = size(19f),
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(12f),
            lineHeight = size(16f),
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = size(13f),
            lineHeight = size(17f),
            letterSpacing = 0.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = size(11f),
            lineHeight = size(14f),
            letterSpacing = 0.4.sp,
        ),
    )
}

val LocalVelaColors = staticCompositionLocalOf {
    darkColors(LimeAccent, amoled = false)
}
val LocalVelaDimens = staticCompositionLocalOf {
    dimensFor(DensityLevel.COMFORTABLE, CornerStyle.ROUNDED, 1f).first
}

object VelaTheme {
    val colors: VelaColors
        @Composable get() = LocalVelaColors.current
    val dimens: VelaDimens
        @Composable get() = LocalVelaDimens.current
}

@Composable
fun VelaTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val accent = accentFor(settings)
    val colors = remember(useDark, accent, settings.themeMode) {
        if (useDark) darkColors(accent, amoled = settings.themeMode == ThemeMode.AMOLED) else lightColors(accent)
    }
    val (dimens, shapes) = remember(settings.density, settings.cornerStyle) {
        dimensFor(settings.density, settings.cornerStyle, settings.fontScale)
    }
    val typography = remember(settings.fontScale) { typographyFor(settings.fontScale) }
    val scheme = remember(colors) {
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.cyan,
                onSecondary = colors.ground,
                tertiary = colors.pink,
                background = colors.ground,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surface2,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.border,
                error = colors.error,
                onError = colors.ground,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = colors.onAccent,
                secondary = colors.cyan,
                onSecondary = colors.surface,
                tertiary = colors.pink,
                background = colors.ground,
                onBackground = colors.textPrimary,
                surface = colors.surface,
                onSurface = colors.textPrimary,
                surfaceVariant = colors.surface2,
                onSurfaceVariant = colors.textSecondary,
                outline = colors.border,
                error = colors.error,
                onError = colors.surface,
            )
        }
    }

    CompositionLocalProvider(
        LocalVelaColors provides colors,
        LocalVelaDimens provides dimens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
