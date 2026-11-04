package com.terrace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 界面语义色板：与设计稿 design/terrace-ui-androidagent.html 一一对应。
 * MaterialTheme 能映射的走 colorScheme，其余（品牌墨色、设置页背景等）走这里。
 */
data class AppPalette(
    val brand: Color,
    val brandInk: Color,
    val brandSoft: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val hair: Color,
    val surface: Color,
    val settingsBg: Color,
    val field: Color,
    val code: Color,
    val bubble: Color,
    val card: Color,
    val divider: Color,
    val success: Color,
    val danger: Color,
)

val LightPalette = AppPalette(
    brand = Color(0xFF426EFE),
    brandInk = Color(0xFF3D63F0),
    brandSoft = Color(0xFFEDF1FF),
    ink = Color(0xFF0F0F0F),
    ink2 = Color(0xFF1A1A1A),
    ink3 = Color(0xFF8A8A8E),
    ink4 = Color(0xFFB0B0B5),
    hair = Color(0xFFEFEFF1),
    surface = Color(0xFFFFFFFF),
    settingsBg = Color(0xFFF5F5F6),
    field = Color(0xFFF4F4F5),
    code = Color(0xFFF7F7F8),
    bubble = Color(0xFFE8EEFC),
    card = Color(0xFFFFFFFF),
    divider = Color(0xFFEFEFF1),
    success = Color(0xFF16A34A),
    danger = Color(0xFFEF4444),
)

val DarkPalette = AppPalette(
    brand = Color(0xFF7B93FF),
    brandInk = Color(0xFFC7D0FF),
    brandSoft = Color(0xFF262A3A),
    ink = Color(0xFFECECEF),
    ink2 = Color(0xFFECECEF),
    ink3 = Color(0xFF9A9AA1),
    ink4 = Color(0xFF6F6F76),
    hair = Color(0xFF2C2C31),
    surface = Color(0xFF151517),
    settingsBg = Color(0xFF101012),
    field = Color(0xFF2A2A2E),
    code = Color(0xFF2B2B2E),
    bubble = Color(0xFF253052),
    card = Color(0xFF1E1E21),
    divider = Color(0xFF2C2C31),
    success = Color(0xFF4ADE80),
    danger = Color(0xFFF87171),
)

@Composable
fun appPalette(dark: Boolean = isSystemInDarkTheme()): AppPalette =
    if (dark) DarkPalette else LightPalette