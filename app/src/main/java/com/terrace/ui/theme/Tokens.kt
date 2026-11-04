package com.terrace.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 设计令牌 — 统一管理圆角 / 间距 / 动效时长，让全局观感一致。
 */
object AppRadii {
    val card = RoundedCornerShape(14.dp)
    val group = RoundedCornerShape(18.dp)
    val toolResult = RoundedCornerShape(10.dp)
    val sessionRow = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(12.dp)
    val input = RoundedCornerShape(26.dp)
    val search = RoundedCornerShape(28.dp)
    val drawer = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    val appIcon = RoundedCornerShape(12.dp)
    val welcomeLogo = RoundedCornerShape(19.dp)

    val userBubble = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomEnd = 6.dp,
        bottomStart = 18.dp,
    )
}

object AppSpacing {
    val pageHorizontal = 18.dp
    val messageGap = 18.dp
    val listTop = 12.dp
    val listBottom = 28.dp
    val drawerWidthFraction = 0.8f
}

object AppElevation {
    val soft = 2.dp
    val drawer = 12.dp
}

object AppMotion {
    const val drawerMs = 280
    const val fastMs = 180
    const val messageMs = 360
    const val settingsMs = 240

    /** cubic-bezier(.22,.61,.36,1) */
    val ease = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
}