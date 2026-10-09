package com.terrace.ui.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terrace.data.db.AppDatabase
import com.terrace.data.model.ChatSession
import com.terrace.data.model.Message
import com.terrace.engine.ChatEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val streamingContent: String = "",
    val streamingReasoning: String = "",
    val sessionTitle: String = "新对话",
    val allSessions: List<ChatSession> = emptyList(),
    val totalHit: Int = 0,
    val totalMiss: Int = 0,
    val totalOut: Int = 0,
    val totalCost: Double = 0.0,
    val model: String = "",
    val balance: String = "",
    val symbol: String = "¥"
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ChatEngine(application)
    private val db = AppDatabase.getInstance(application)

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var currentSessionId: String = ""
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            db.sessionDao().getAllSessions().collect { sessions ->
                uiState = uiState.copy(allSessions = sessions)
                sessions.find { it.id == currentSessionId }?.let {
                    uiState = uiState.copy(sessionTitle = it.title)
                }
            }
        }
    }

    fun initSession(sessionId: String) {
        if (sessionId == "new") {
            viewModelScope.launch {
                currentSessionId = engine.createSession()
                uiState = uiState.copy(messages = emptyList(), error = null)
                loadMessages()
            }
        } else {
            currentSessionId = sessionId
            uiState = uiState.copy(messages = emptyList(), error = null)
            loadMessages()
        }
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == currentSessionId || uiState.isLoading) return
        currentSessionId = sessionId
        uiState = uiState.copy(messages = emptyList(), error = null)
        loadMessages()
    }

    fun startNewSession() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            currentSessionId = engine.createSession()
            uiState = uiState.copy(messages = emptyList(), error = null)
            loadMessages()
        }
    }

    private fun loadMessages() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            db.messageDao().getMessagesBySession(currentSessionId).collect { messages ->
                val session = db.sessionDao().getSession(currentSessionId)
                uiState = uiState.copy(
                    messages = messages,
                    sessionTitle = session?.title ?: "新对话",
                    totalHit = session?.totalHit ?: 0,
                    totalMiss = session?.totalMiss ?: 0,
                    totalOut = session?.totalOut ?: 0,
                    totalCost = session?.totalCost ?: 0.0,
                    model = session?.model ?: "",
                    balance = session?.balance ?: "",
                    symbol = session?.symbol ?: "¥"
                )
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || uiState.isLoading || currentSessionId.isBlank()) return
        uiState = uiState.copy(
            isLoading = true, error = null,
            streamingContent = "", streamingReasoning = ""
        )
        viewModelScope.launch {
            val result = engine.sendMessage(
                sessionId = currentSessionId,
                question = text,
                onContent = { delta ->
                    uiState = uiState.copy(streamingContent = uiState.streamingContent + delta)
                },
                onReasoning = { delta ->
                    uiState = uiState.copy(streamingReasoning = uiState.streamingReasoning + delta)
                },
                onRoundComplete = {
                    // 本轮已按时间线落库，清空流式缓冲，改由数据库消息渲染
                    uiState = uiState.copy(streamingContent = "", streamingReasoning = "")
                }
            )
            result.fold(
                onSuccess = {
                    uiState = uiState.copy(
                        isLoading = false, error = null,
                        streamingContent = "", streamingReasoning = ""
                    )
                },
                onFailure = { e ->
                    uiState = uiState.copy(
                        isLoading = false,
                        error = e.message ?: "未知错误",
                        streamingContent = "", streamingReasoning = ""
                    )
                }
            )
            // 回复结束后重新读取会话，确保 token / 费用 / 余额已同步
            loadMessages()
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    fun getSessionId(): String = currentSessionId
}
