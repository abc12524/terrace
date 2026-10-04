package com.shelltool.android.engine

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.shelltool.android.data.api.ShellToolClient
import com.shelltool.android.data.db.AppDatabase
import com.shelltool.android.data.model.ChatSession
import com.shelltool.android.data.model.Message
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

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        db.messageDao().deleteBySession(sessionId)
        db.sessionDao().deleteById(sessionId)
    }

    /**
     * 发送一轮对话：先落用户消息，消费 SSE，再落 assistant / tool 消息。
     *
     * @param onContent 正文增量回调（用于流式 UI）
     * @param onReasoning 思考增量回调
     */
    suspend fun sendMessage(
        sessionId: String,
        question: String,
        onContent: (String) -> Unit,
        onReasoning: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = db.sessionDao().getSession(sessionId)
            ?: return@withContext Result.failure(Exception("会话不存在"))

        val userMsg = Message(sessionId = sessionId, role = "user", content = question)
        db.messageDao().insert(userMsg)
        if (session.title == "新对话" && question.isNotBlank()) {
            db.sessionDao().updateTitle(sessionId, question.take(20))
        }

        val localMessages = mutableListOf<Message>()
        val finalContent = StringBuilder()
        val roundReasoning = StringBuilder()
        val allReasoning = StringBuilder()
        var roundTools = mutableListOf<ToolEntry>()
        var usage: UsageStats? = null
        var serverError: String? = null

        suspend fun flushRound() {
            if (roundTools.isEmpty()) return
            val rc = roundReasoning.toString()
            val calls = JsonArray()
            roundTools.forEach { t ->
                calls.add(JsonObject().apply {
                    addProperty("id", t.id)
                    addProperty("name", t.name)
                    addProperty("arguments", t.args ?: "")
                })
            }
            localMessages.add(
                Message(
                    sessionId = sessionId,
                    role = "assistant",
                    content = "",
                    reasoningContent = rc.ifBlank { null },
                    toolCalls = calls.toString()
                )
            )
            roundTools.forEach { t ->
                localMessages.add(
                    Message(
                        sessionId = sessionId,
                        role = "tool",
                        content = t.output ?: "",
                        toolCallId = t.id,
                        toolName = t.name,
                        toolArgs = t.args
                    )
                )
            }
            roundTools = mutableListOf()
            roundReasoning.setLength(0)
        }

        try {
            client.stream(question, new = !session.serverStarted).collect { ev ->
                when (ev.type) {
                    "content" -> {
                        val delta = ev.str("content") ?: ""
                        if (delta.isNotEmpty()) {
                            finalContent.append(delta)
                            onContent(delta)
                        }
                    }
                    "reasoning" -> {
                        val delta = ev.str("content") ?: ""
                        if (delta.isNotEmpty()) {
                            roundReasoning.append(delta)
                            allReasoning.append(delta)
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
                    "continuing" -> flushRound()
                    "session" -> {
                        if (!session.serverStarted) {
                            db.sessionDao().setServerStarted(sessionId, true)
                        }
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

        // 收尾：补齐工具轮与最终 assistant 消息
        val finalReasoning: String?
        if (roundTools.isNotEmpty()) {
            flushRound()
            finalReasoning = null
        } else {
            finalReasoning = roundReasoning.toString().ifBlank { allReasoning.toString() }.ifBlank { null }
        }
        // 出错且没有任何输出时，不再落一条空回复
        if (finalContent.isNotEmpty() || finalReasoning != null) {
            localMessages.add(
                Message(
                    sessionId = sessionId,
                    role = "assistant",
                    content = finalContent.toString(),
                    reasoningContent = finalReasoning,
                    promptTokens = usage?.let { it.hit + it.miss } ?: 0,
                    completionTokens = usage?.out ?: 0
                )
            )
        }
        localMessages.forEach { db.messageDao().insert(it) }

        val count = db.messageDao().getMessagesBySessionSync(sessionId).size
        db.sessionDao().updateStats(sessionId, System.currentTimeMillis(), count)
        usage?.let {
            db.sessionDao().addUsage(
                sessionId, it.hit, it.miss, it.out, it.cost, it.model, it.balance, it.symbol
            )
        }

        val hasTool = localMessages.any { it.role == "tool" }
        if (serverError != null && finalContent.isEmpty() && !hasTool) {
            Result.failure(Exception(serverError))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun checkHealth(): Result<String> = client.health()
}
