package com.terrace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 设计令牌 — 统一管理圆角 / 间距 / 阴影，让全局观感一致。
 */
object AppRadii {
    val card = RoundedCornerShape(14.dp)
    val capsule = RoundedCornerShape(28.dp)
    val bubble = RoundedCornerShape(16.dp)
    val timelineCard = RoundedCornerShape(16.dp)
}

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
}

object AppElevation {
    val soft = 2.dp
    val card = 1.dp
}

object AppShadows {
    val soft = Shadow(
        color = Color.Black.copy(alpha = 0.05f),
        blurRadius = 18f,
        offset = androidx.compose.ui.geometry.Offset(0f, 6f),
    )
}
