package com.terrace.ui.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.terrace.data.api.ShellToolClient
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
    val symbol: String = "¥",
    /** 本轮 id：中途插话要用它指认目标（来自 SSE 的 turn 事件） */
    val turnId: String? = null,
    /** 服务端是否开了中途插话（同样是 turn 事件里带的） */
    val interjectSupported: Boolean = false,
    /** 正在等 /interject 回执（这期间别重复发） */
    val interjecting: Boolean = false,
    /** 一次性提示（插话成功/已排队等），用 Snackbar 展示 */
    val notice: String? = null,
    /** 排队中的条数：服务端插不了话时，这些话等本轮结束自动发 */
    val pendingCount: Int = 0
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = ChatEngine(application)
    private val db = AppDatabase.getInstance(application)

    var uiState by mutableStateOf(ChatUiState())
        private set

    private var currentSessionId: String = ""
    private var loadJob: Job? = null

    /** 插不进本轮、等着本轮结束后再发的消息（只在当前会话内有效） */
    private val pending = ArrayDeque<String>()

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
                resetPending()
                currentSessionId = engine.createSession()
                uiState = uiState.copy(messages = emptyList(), error = null)
                loadMessages()
            }
        } else {
            resetPending()
            currentSessionId = sessionId
            uiState = uiState.copy(messages = emptyList(), error = null)
            loadMessages()
        }
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == currentSessionId || uiState.isLoading) return
        resetPending()
        currentSessionId = sessionId
        uiState = uiState.copy(messages = emptyList(), error = null)
        loadMessages()
    }

    fun startNewSession() {
        if (uiState.isLoading) return
        viewModelScope.launch {
            resetPending()
            currentSessionId = engine.createSession()
            uiState = uiState.copy(messages = emptyList(), error = null)
            loadMessages()
        }
    }

    private fun resetPending() {
        if (pending.isEmpty()) return
        pending.clear()
        uiState = uiState.copy(pendingCount = 0)
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

    /**
     * 输入框的统一出口：没在跑就发新一轮，正在跑就插话。
     *
     * 规则是「能插就插，插不了就当后续轮次的新问题」：
     * - 插得进：这句话作为 user 消息插进正在跑的那一轮，模型下一步就能看到；
     * - 插不进（服务端不支持 / 这一轮已过工具边界 / 已结束）：排进队列，本轮结束后自动发出去。
     *
     * 不管哪条路，消息都会**立刻**出现在主界面（先落本地库，再等服务端回执），
     * 用户不会出现"发了但界面上什么都没有"的空窗。
     */
    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty() || currentSessionId.isBlank()) return
        if (!uiState.isLoading) {
            sendMessage(t)
            return
        }

        val sessionId = currentSessionId
        val turnId = uiState.turnId
        // interjecting 期间又来一句：直接排队，不并发打服务端
        val canInterject = uiState.interjectSupported &&
            turnId != null && !uiState.interjecting

        if (canInterject) uiState = uiState.copy(interjecting = true)

        viewModelScope.launch {
            // 先落库：主界面立刻看到这句话（是不是插话，看能不能插）
            engine.insertUserMessage(sessionId, t, interjected = canInterject)
            if (sessionId == currentSessionId) loadMessages()

            if (!canInterject) {
                enqueue(t)
                uiState = uiState.copy(interjecting = false)
                return@launch
            }

            when (val r = engine.interject(t, turnId)) {
                is ShellToolClient.Interject.Injected ->
                    uiState = uiState.copy(notice = "已插入本轮，模型会照新要求继续")
                ShellToolClient.Interject.Idle -> enqueue(t)
                is ShellToolClient.Interject.NotDelivered -> enqueue(t)
                ShellToolClient.Interject.Unsupported -> enqueue(t)
                is ShellToolClient.Interject.Failed -> {
                    uiState = uiState.copy(notice = "插话没成功（${r.message}），已排到本轮结束后发送")
                    enqueue(t)
                }
            }
            uiState = uiState.copy(interjecting = false)
        }
    }

    /** 排进队列，本轮结束后作为新问题发出；若本轮已经结束就立刻发 */
    private fun enqueue(text: String) {
        if (!uiState.isLoading) {
            uiState = uiState.copy(notice = "本轮已结束，已作为新消息发送")
            sendMessage(text, persistUserMessage = false)
            return
        }
        pending.addLast(text)
        uiState = uiState.copy(
            notice = "这一轮插不进去，已排队，本轮结束后自动发送",
            pendingCount = pending.size,
        )
    }

    /** 本轮结束：把排队的下一条放出去（消息早已落库，别再插一条） */
    private fun flushPending() {
        if (uiState.isLoading || pending.isEmpty()) return
        val next = pending.removeFirst()
        uiState = uiState.copy(pendingCount = pending.size)
        sendMessage(next, persistUserMessage = false)
    }

    fun sendMessage(text: String, persistUserMessage: Boolean = true) {
        if (text.isBlank() || uiState.isLoading || currentSessionId.isBlank()) return
        uiState = uiState.copy(
            isLoading = true, error = null,
            streamingContent = "", streamingReasoning = "",
            turnId = null, interjectSupported = false
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
                },
                onTurn = { turnId ->
                    // 服务端登记了本轮（只有支持插话的版本才发 turn）：可以插话了
                    uiState = uiState.copy(turnId = turnId, interjectSupported = true)
                },
                persistUserMessage = persistUserMessage
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
            // 本轮结束：清掉插话目标（旧 id 已经过期），再放排队的下一条
            uiState = uiState.copy(turnId = null, interjectSupported = false)
            // 回复结束后重新读取会话，确保 token / 费用 / 余额已同步
            loadMessages()
            flushPending()
        }
    }

    /** 重新生成：重发本轮最近的用户问题。 */
    fun regenerate() {
        if (uiState.isLoading) return
        val lastUser = uiState.messages.lastOrNull { it.role == "user" }?.content
        if (!lastUser.isNullOrBlank()) sendMessage(lastUser)
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    fun clearNotice() {
        uiState = uiState.copy(notice = null)
    }

    fun getSessionId(): String = currentSessionId
}
