package com.terrace.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.terrace.data.AppPreferences
import com.terrace.data.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * shell-tool HTTP API 客户端。
 *
 * 只做两件事：调用 /chat/stream 消费语义事件流，以及 /health 探活。
 * 会话历史完全由本地 SQLite 维护，不在服务端读取。
 */
class ShellToolClient {

    private val jsonMediaType = "application/json".toMediaType()

    /** 服务端语义事件：type + 原始 JSON 字段 */
    data class ShellEvent(val type: String, val json: JsonObject) {
        fun str(key: String): String? =
            json.get(key)?.takeIf { !it.isJsonNull }?.asString

        fun int(key: String, def: Int = 0): Int =
            json.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: def

        fun double(key: String, def: Double = 0.0): Double =
            json.get(key)?.takeIf { !it.isJsonNull }?.asDouble ?: def

        fun bool(key: String, def: Boolean = false): Boolean =
            json.get(key)?.takeIf { !it.isJsonNull }?.asBoolean ?: def

        companion object {
            fun error(message: String): ShellEvent =
                ShellEvent("error", JsonObject().apply { addProperty("error", message) })
        }
    }

    /**
     * 发起一轮对话，返回服务端事件流。
     *
     * @param question 用户问题
     * @param new 是否新开服务端会话（首条消息传 true，对应 dp.py 的 -n）
     */
    fun stream(question: String, new: Boolean): Flow<ShellEvent> = flow {
        val baseUrl = AppPreferences.baseUrl()
        if (baseUrl.isBlank()) {
            emit(ShellEvent.error("请先在设置中配置服务地址（host / port）"))
            return@flow
        }

        val payload = JsonObject().apply {
            addProperty("question", question)
            if (new) addProperty("new", true)
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/stream")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            HttpClientProvider.stream.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    emit(ShellEvent.error("HTTP ${response.code}: $body"))
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ShellEvent.error("响应体为空"))
                    return@flow
                }

                val eventLines = mutableListOf<String>()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (eventLines.isNotEmpty()) {
                            parseEvent(eventLines)?.let { emit(it) }
                            eventLines.clear()
                        }
                        continue
                    }
                    if (line.startsWith(":")) continue // 心跳 / 注释
                    eventLines.add(line)
                }
                if (eventLines.isNotEmpty()) {
                    parseEvent(eventLines)?.let { emit(it) }
                }
            }
        } catch (e: Exception) {
            emit(ShellEvent.error("连接失败: ${e.message ?: e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)

    /** 探活 GET /health */
    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = AppPreferences.baseUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("请先配置服务地址"))
        }
        try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            HttpClientProvider.short.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Result.success(body)
                else Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun parseEvent(lines: List<String>): ShellEvent? {
        val data = lines
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }
        if (data.isBlank()) return null
        return try {
            val obj = JsonParser.parseString(data).asJsonObject
            val type = obj.get("type")?.takeIf { !it.isJsonNull }?.asString ?: return null
            ShellEvent(type, obj)
        } catch (_: Exception) {
            null
        }
    }
}
