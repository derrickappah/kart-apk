package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class KartExtendedColors(
    val isDark: Boolean,
    val primaryDark: Color,
    val primaryLight: Color,
    val categoryTile: Color,
    val chatBackground: Color,
    val authBackground: Color,
    val cardBorder: Color,
    val subtleDivider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val chipActiveBg: Color,
    val chipActiveContent: Color,
    val chipInactiveBg: Color,
    val chipInactiveContent: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val wishlistPillBg: Color,
    val wishlistPillBorder: Color,
    val wishlistIconInactive: Color,
    val conditionCardBg: Color,
    val conditionIconBoxBg: Color,
    val conditionTagBg: Color,
    val conditionTagText: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

private val LightExtendedColors = KartExtendedColors(
    isDark = false,
    primaryDark = KartPrimaryDark,
    primaryLight = KartPrimaryLight,
    categoryTile = KartCategoryTileLight,
    chatBackground = KartChatBgLight,
    authBackground = KartAuthBgLight,
    cardBorder = KartBorderLight,
    subtleDivider = KartDividerLight,
    textPrimary = KartTextPrimaryLight,
    textSecondary = KartTextSecondaryLight,
    textMuted = KartTextMutedLight,
    chipActiveBg = KartTextPrimaryLight,
    chipActiveContent = Color.White,
    chipInactiveBg = Color(0xFFF3F4F6),
    chipInactiveContent = Color(0xFF4B5563),
    shimmerBase = Color(0xFFF3F4F6),
    shimmerHighlight = Color(0xFFFFFFFF),
    wishlistPillBg = Color.White.copy(alpha = 0.80f),
    wishlistPillBorder = Color.White.copy(alpha = 0.60f),
    wishlistIconInactive = Color(0xFF4B5563),
    conditionCardBg = Color(0xFFF9FAFB),
    conditionIconBoxBg = Color.White,
    conditionTagBg = Color(0xFFF3F4F6),
    conditionTagText = Color(0xFF374151),
    success = KartSuccess,
    warning = KartWarning,
    danger = KartDanger
)

private val DarkExtendedColors = KartExtendedColors(
    isDark = true,
    primaryDark = KartPrimaryDark,
    primaryLight = KartPrimaryLight,
    categoryTile = KartCategoryTileDark,
    chatBackground = KartChatBgDark,
    authBackground = KartAuthBgDark,
    cardBorder = KartBorderDark,
    subtleDivider = KartDividerDark,
    textPrimary = KartTextPrimaryDark,
    textSecondary = KartTextSecondaryDark,
    textMuted = KartTextMutedDark,
    chipActiveBg = Color.White,
    chipActiveContent = KartTextPrimaryLight,
    chipInactiveBg = KartSurfaceDark,
    chipInactiveContent = Color(0xFFD1D5DB),
    shimmerBase = Color(0xFF2F2F35),
    shimmerHighlight = Color(0xFF3E3E46),
    wishlistPillBg = Color.Black.copy(alpha = 0.40f),
    wishlistPillBorder = Color.White.copy(alpha = 0.16f),
    wishlistIconInactive = Color.White,
    conditionCardBg = Color(0xFF25292E),
    conditionIconBoxBg = Color(0xFF1E2227),
    conditionTagBg = Color(0xFF2F2F35),
    conditionTagText = Color(0xFFE5E7EB),
    success = KartSuccess,
    warning = KartWarning,
    danger = KartDanger
)

val LocalKartColors = staticCompositionLocalOf { LightExtendedColors }

private val KartLightColorScheme = lightColorScheme(
    primary = KartPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F5FC),
    onPrimaryContainer = Color(0xFF0A4B63),
    secondary = KartCharcoal,
    onSecondary = Color.White,
    secondaryContainer = KartSurfaceLight,
    onSecondaryContainer = KartTextPrimaryLight,
    tertiary = KartPrimaryLight,
    onTertiary = Color.White,
    background = KartBackgroundLight,
    onBackground = KartTextPrimaryLight,
    surface = KartBackgroundLight,
    onSurface = KartTextPrimaryLight,
    surfaceVariant = KartSurfaceLight,
    onSurfaceVariant = KartTextSecondaryLight,
    outline = KartDividerLight,
    outlineVariant = KartBorderLight,
    error = KartDanger,
    onError = Color.White
)

private val KartDarkColorScheme = darkColorScheme(
    primary = KartPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0E4960),
    onPrimaryContainer = Color(0xFFD2F1FC),
    secondary = Color.White,
    onSecondary = KartTextPrimaryLight,
    secondaryContainer = KartSurfaceDark,
    onSecondaryContainer = KartTextPrimaryDark,
    tertiary = KartPrimaryLight,
    onTertiary = KartTextPrimaryLight,
    background = KartBackgroundDark,
    onBackground = KartTextPrimaryDark,
    surface = KartBackgroundDark,
    onSurface = KartTextPrimaryDark,
    surfaceVariant = KartSurfaceDark,
    onSurfaceVariant = KartTextSecondaryDark,
    outline = KartBorderDark,
    outlineVariant = KartDividerDark,
    error = KartDanger,
    onError = Color.White
)

val KartShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object KartThemeTokens {
    val colors: KartExtendedColors
        @Composable get() = LocalKartColors.current
}

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun KartTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (darkTheme) KartDarkColorScheme else KartLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalKartColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KartTypography,
            shapes = KartShapes,
            content = content
        )
    }
}
