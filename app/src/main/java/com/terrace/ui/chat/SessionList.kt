package com.terrace.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz

import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terrace.data.model.ChatSession
import com.terrace.ui.theme.AppRadii
import com.terrace.ui.theme.HapticsType
import com.terrace.ui.theme.appPalette
import com.terrace.ui.theme.rememberHaptics
import java.util.Calendar

/**
 * 会话列表的公共内容：对话页推拉抽屉与独立会话页共用同一套实现。
 */
@Composable
internal fun SessionSearchBar(onClick: (() -> Unit)? = null) {
    val p = appPalette()
    Box(
        Modifier
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(AppRadii.search)
                .background(p.ink.copy(alpha = 0.05f))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else Modifier
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = p.ink3,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "搜索对话内容...",
                fontSize = 15.sp,
                color = p.ink3.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
internal fun SessionListBody(
    sessions: List<ChatSession>,
    currentId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = appPalette()
    val groups = remember(sessions) {
        groupSessions(sessions.filter { it.messageCount > 0 })
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        groups.forEach { group ->
            item(key = "hdr_${group.label}") {
                Text(
                    group.label,
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                    fontSize = 13.sp,
                    color = p.ink3.copy(alpha = 0.85f),
                )
            }
            items(group.sessions, key = { it.id }) { session ->
                SessionTile(
                    session = session,
                    active = session.id == currentId,
                    onClick = { onSelect(session.id) },
                )
            }
        }
    }
}

@Composable
internal fun SessionAccountRow(
    onSettings: () -> Unit,
    onAccountClick: (() -> Unit)? = null,
) {
    val p = appPalette()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .then(
                    if (onAccountClick != null) {
                        Modifier.clickable(onClick = onAccountClick)
                    } else Modifier
                )
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(p.ink.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                AvatarGlyph(color = p.ink3)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "terrace",
                fontSize = 15.sp,
                color = p.ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = "设置",
                    tint = p.ink3,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun AvatarGlyph(color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val s = size.minDimension / 24f
        val stroke = 1.8f * s
        drawCircle(
            color, radius = 3.6f * s,
            center = Offset(12f * s, 8.5f * s),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(5f * s, 13f * s),
            size = androidx.compose.ui.geometry.Size(14f * s, 11f * s),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                stroke, cap = StrokeCap.Round,
            ),
        )
    }
}

@Composable
private fun SessionTile(session: ChatSession, active: Boolean, onClick: () -> Unit) {
    val p = appPalette()
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed) p.ink.copy(alpha = 0.05f) else Color.Transparent

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(AppRadii.sessionRow)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null) {
                haptics(HapticsType.Light)
                onClick()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (session.title.isBlank()) "未命名对话" else session.title,
            fontSize = 16.sp,
            color = if (active) p.brand else p.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ==================== 分组 ====================

private data class SessionGroup(val label: String, val sessions: List<ChatSession>)

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, d: Calendar): Boolean {
    val y = Calendar.getInstance().also { it.timeInMillis = now.timeInMillis - 86400000L }
    return sameDay(y, d)
}

private fun groupSessions(all: List<ChatSession>): List<SessionGroup> {
    val now = Calendar.getInstance()
    val weekStart = Calendar.getInstance().also {
        it.add(Calendar.DAY_OF_WEEK, it.firstDayOfWeek - it.get(Calendar.DAY_OF_WEEK))
        it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0)
        it.set(Calendar.SECOND, 0); it.set(Calendar.MILLISECOND, 0)
    }
    val today = mutableListOf<ChatSession>()
    val yesterday = mutableListOf<ChatSession>()
    val week = mutableListOf<ChatSession>()
    val earlier = mutableListOf<ChatSession>()
    for (session in all.sortedByDescending { it.createdAt }) {
        val c = Calendar.getInstance().also { it.timeInMillis = session.createdAt }
        when {
            sameDay(now, c) -> today.add(session)
            isYesterday(now, c) -> yesterday.add(session)
            c.timeInMillis >= weekStart.timeInMillis -> week.add(session)
            else -> earlier.add(session)
        }
    }
    val result = mutableListOf<SessionGroup>()
    if (today.isNotEmpty()) result.add(SessionGroup("今天", today))
    if (yesterday.isNotEmpty()) result.add(SessionGroup("昨天", yesterday))
    if (week.isNotEmpty()) result.add(SessionGroup("本周", week))
    if (earlier.isNotEmpty()) result.add(SessionGroup("更早", earlier))
    return result
}
