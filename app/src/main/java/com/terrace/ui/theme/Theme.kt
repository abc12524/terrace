package com.terrace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColorScheme = lightColorScheme(
    primary = LightPalette.brand,
    onPrimary = Color.White,
    primaryContainer = LightPalette.bubble,
    onPrimaryContainer = LightPalette.ink,
    inversePrimary = DarkPalette.brand,
    secondary = LightPalette.brandInk,
    onSecondary = Color.White,
    secondaryContainer = LightPalette.brandSoft,
    onSecondaryContainer = LightPalette.brandInk,
    tertiary = LightPalette.brandInk,
    onTertiary = Color.White,
    tertiaryContainer = LightPalette.brandSoft,
    onTertiaryContainer = LightPalette.brandInk,
    background = LightPalette.surface,
    onBackground = LightPalette.ink,
    surface = LightPalette.surface,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.field,
    onSurfaceVariant = LightPalette.ink3,
    surfaceTint = LightPalette.brand,
    inverseSurface = LightPalette.ink2,
    inverseOnSurface = DarkPalette.ink,
    error = LightPalette.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
    outline = Color(0xFFD5D5DA),
    outlineVariant = LightPalette.hair,
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFEDEDEF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightPalette.code,
    surfaceContainer = LightPalette.field,
    surfaceContainerHigh = LightPalette.hair,
    surfaceContainerHighest = Color(0xFFE9E9EC),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPalette.brand,
    onPrimary = Color(0xFF10121A),
    primaryContainer = DarkPalette.bubble,
    onPrimaryContainer = DarkPalette.ink,
    inversePrimary = LightPalette.brand,
    secondary = DarkPalette.brandInk,
    onSecondary = Color(0xFF10121A),
    secondaryContainer = DarkPalette.brandSoft,
    onSecondaryContainer = DarkPalette.brandInk,
    tertiary = DarkPalette.brandInk,
    onTertiary = Color(0xFF10121A),
    tertiaryContainer = DarkPalette.brandSoft,
    onTertiaryContainer = DarkPalette.brandInk,
    background = DarkPalette.surface,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.field,
    onSurfaceVariant = DarkPalette.ink3,
    surfaceTint = DarkPalette.brand,
    inverseSurface = DarkPalette.ink,
    inverseOnSurface = LightPalette.surface,
    error = DarkPalette.danger,
    onError = Color(0xFF10121A),
    errorContainer = Color(0xFF4A1D1D),
    onErrorContainer = Color(0xFFFECACA),
    outline = Color(0xFF3C3C42),
    outlineVariant = DarkPalette.hair,
    scrim = Color.Black,
    surfaceBright = Color(0xFF232327),
    surfaceDim = DarkPalette.surface,
    surfaceContainerLowest = DarkPalette.settingsBg,
    surfaceContainerLow = Color(0xFF1A1A1D),
    surfaceContainer = DarkPalette.card,
    surfaceContainerHigh = Color(0xFF26262A),
    surfaceContainerHighest = Color(0xFF2E2E33),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun ShellToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}