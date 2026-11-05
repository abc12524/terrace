package com.terrace.engine

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.terrace.data.api.ShellToolClient
import com.terrace.data.db.AppDatabase
import com.terrace.data.model.ChatSession
import com.terrace.data.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 对话引擎：把服务端 SSE 语义事件映射成本地消息并落 SQLite。
 *
 * 历史只存本地，服务端不参与；服务端会话仅靠首条消息的 -n 建立。
 */
class ChatEngine(private val context: Context) {

    private val client = ShellToolClient()
    private val db = AppDatabase.getInstance(context)

    data class UsageStats(
        val hit: Int,
        val miss: Int,
        val out: Int,
        val cost: Double,
        val model: String,
        val balance: String,
        val symbol: String
    )

    private data class ToolEntry(
        val id: String,
        val name: String,
        val args: String?,
        val output: String?
    )

    suspend fun createSession(): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().take(8)
        db.sessionDao().insert(ChatSession(id = id, title = "新对话"))
        id
    }

    /**
     * 本地记一条用户消息。
     *
     * 中途插话走的是服务端的 /interject，服务端把它作为 user 消息塞进它自己的上下文；
     * 本地这份历史是我们自己渲染的，所以这里补一条，用户才看得到自己刚说的话。
     */
    suspend fun insertUserMessage(sessionId: String, text: String, interjected: Boolean) =
        withContext(Dispatchers.IO) {
            val session = db.sessionDao().getSession(sessionId) ?: return@withContext
            db.messageDao().insert(
                Message(
                    sessionId = sessionId,
                    role = "user",
                    content = text,
                    interjected = interjected,
                )
            )
            if (session.title == "新对话" && text.isNotBlank()) {
                db.sessionDao().updateTitle(sessionId, text.take(20))
            }
            val count = db.messageDao().getMessagesBySessionSync(sessionId).size
            db.sessionDao().updateStats(sessionId, System.currentTimeMillis(), count)
        }

    /** 把一句话递给正在跑的那一轮（服务端决定落点：工具边界） */
    suspend fun interject(text: String, turnId: String?): ShellToolClient.Interject =
        client.interject(text, turnId)

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        db.messageDao().deleteBySession(sessionId)
        db.sessionDao().deleteById(sessionId)
    }

    /**
     * 发送一轮对话：先落用户消息，消费 SSE，按「工具轮」边界逐轮落 assistant / tool 消息。
     *
     * 每遇到 continuing（工具轮边界）就把当前轮的 reasoning + content + tool_calls / tool 结果
     * 按时间线写入数据库并通知 UI 复位流式缓冲，因此正文不会全部堆到最后的正式回复里。
     *
     * @param onContent 正文增量回调（用于流式 UI）
     * @param onReasoning 思考增量回调
     * @param onRoundComplete 一轮落库完成回调：UI 应清空当前流式缓冲，改由数据库消息渲染
     * @param onTurn 本轮登记回调：turnId 用于中途插话。
     *        服务端只在能接插话时才会发 `turn`（Python 版没有这个事件），所以收到即视为可用
     * @param persistUserMessage 是否把这句话落成本地 user 消息。插话那条在发送时已经落过了，
     *        轮到它作为新一轮真正发出去时要传 false，否则历史里会出现两条一样的
     */
    suspend fun sendMessage(
        sessionId: String,
        question: String,
        onContent: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onRoundComplete: () -> Unit,
        onTurn: (turnId: String) -> Unit = {},
        persistUserMessage: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = db.sessionDao().getSession(sessionId)
            ?: return@withContext Result.failure(Exception("会话不存在"))

        if (persistUserMessage) {
            val userMsg = Message(sessionId = sessionId, role = "user", content = question)
            db.messageDao().insert(userMsg)
        }
        if (session.title == "新对话" && question.isNotBlank()) {
            db.sessionDao().updateTitle(sessionId, question.take(20))
        }

        var roundReasoning = StringBuilder()
        var roundContent = StringBuilder()
        var roundTools = mutableListOf<ToolEntry>()
        var usage: UsageStats? = null
        var serverError: String? = null
        var producedOutput = false
        var sawTool = false

        /**
         * 把当前轮按时间线落库：一条 assistant（content + reasoning + toolCalls）+ 若干条 tool。
         * 落库后清空本轮缓冲。usageStats 仅在该轮为最后一轮时传入，用于记录 token。
         */
        suspend fun flushRound(usageStats: UsageStats?) {
            val rc = roundReasoning.toString()
            val cc = roundContent.toString()
            val hasTools = roundTools.isNotEmpty()
            if (!hasTools && cc.isBlank() && rc.isBlank()) return

            val calls = if (hasTools) {
                JsonArray().apply {
                    roundTools.forEach { t ->
                        add(JsonObject().apply {
                            addProperty("id", t.id)
                            addProperty("name", t.name)
                            addProperty("arguments", t.args ?: "")
                        })
                    }
                }.toString()
            } else null

            db.messageDao().insert(
                Message(
                    sessionId = sessionId,
                    role = "assistant",
                    content = cc,
                    reasoningContent = rc.ifBlank { null },
                    toolCalls = calls,
                    promptTokens = usageStats?.let { it.hit + it.miss } ?: 0,
                    completionTokens = usageStats?.out ?: 0
                )
            )
            roundTools.forEach { t ->
                db.messageDao().insert(
                    Message(
                        sessionId = sessionId,
                        role = "tool",
                        content = t.output ?: "",
                        toolCallId = t.id,
                        toolName = t.name,
                        toolArgs = t.args
                    )
                )
                sawTool = true
            }
            producedOutput = true
            roundReasoning = StringBuilder()
            roundContent = StringBuilder()
            roundTools = mutableListOf()
        }

        try {
            client.stream(question, new = !session.serverStarted).collect { ev ->
                when (ev.type) {
                    "content" -> {
                        val delta = ev.str("content") ?: ""
                        if (delta.isNotEmpty()) {
                            roundContent.append(delta)
                            onContent(delta)
                        }
                    }
                    "reasoning" -> {
                        val delta = ev.str("content") ?: ""
                        if (delta.isNotEmpty()) {
                            roundReasoning.append(delta)
                            onReasoning(delta)
                        }
                    }
                    "tool_call" -> {
                        roundTools.add(
                            ToolEntry(
                                id = ev.str("id") ?: "",
                                name = ev.str("name") ?: "",
                                args = ev.str("arguments"),
                                output = null
                            )
                        )
                    }
                    "tool_result" -> {
                        val id = ev.str("id")
                        val output = ev.str("output") ?: ""
                        val idx = roundTools.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            roundTools[idx] = roundTools[idx].copy(output = output)
                        } else {
                            roundTools.add(ToolEntry(id ?: "", ev.str("name") ?: "", null, output))
                        }
                    }
                    "continuing" -> {
                        // 工具轮边界：当前轮按时间线落库，并让 UI 复位流式缓冲
                        flushRound(null)
                        onRoundComplete()
                    }
                    "session" -> {
                        if (!session.serverStarted) {
                            db.sessionDao().setServerStarted(sessionId, true)
                        }
                    }
                    "turn" -> {
                        // 本轮登记：id 是 /interject 的插话目标。
                        // 服务端只在能接插话的版本上发这个事件，收到它就说明可以插
                        val turnId = ev.str("id") ?: ""
                        if (turnId.isNotBlank()) onTurn(turnId)
                    }
                    "usage" -> {
                        usage = UsageStats(
                            hit = ev.int("hit"),
                            miss = ev.int("miss"),
                            out = ev.int("out"),
                            cost = ev.double("total"),
                            model = ev.str("model") ?: "",
                            balance = ev.str("balance") ?: "",
                            symbol = ev.str("symbol") ?: "¥"
                        )
                    }
                    "error" -> serverError = ev.str("error") ?: "服务端错误"
                }
            }
        } catch (e: Exception) {
            serverError = e.message ?: e.javaClass.simpleName
        }

        // 收尾：落最后一轮（此时 usage 已就绪），并复位流式缓冲
        flushRound(usage)
        onRoundComplete()

        // 先写会话统计/用量，UI 会话流随后刷新，token / 费用已就绪。
        val count = db.messageDao().getMessagesBySessionSync(sessionId).size
        db.sessionDao().updateStats(sessionId, System.currentTimeMillis(), count)
        usage?.let {
            db.sessionDao().addUsage(
                sessionId, it.hit, it.miss, it.out, it.cost, it.model, it.balance, it.symbol
            )
        }

        if (serverError != null && !producedOutput && !sawTool) {
            Result.failure(Exception(serverError))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun checkHealth(): Result<String> = client.health()
}
