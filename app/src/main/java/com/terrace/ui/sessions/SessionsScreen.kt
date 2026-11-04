package com.terrace.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.terrace.data.model.ChatSession
import com.terrace.ui.chat.SessionAccountRow
import com.terrace.ui.chat.SessionListBody
import com.terrace.ui.chat.SessionSearchBar
import com.terrace.ui.theme.appPalette

/**
 * 独立会话整页：与对话页抽屉共用同一套列表内容。
 */
@Composable
fun SessionsScreen(
    sessions: List<ChatSession>,
    currentId: String,
    onSelect: (String) -> Unit,
    onSettings: () -> Unit,
) {
    val p = appPalette()
    Column(Modifier.fillMaxSize().background(p.surface)) {
        SessionSearchBar()
        SessionListBody(
            sessions = sessions,
            currentId = currentId,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        SessionAccountRow(onSettings = onSettings)
    }
}