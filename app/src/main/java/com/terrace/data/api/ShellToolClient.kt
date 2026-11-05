package com.terrace.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.terrace.data.AppPreferences
import com.terrace.data.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
        // 类型安全取值：对象/数组字段（如 tool_call.arguments）返回其 JSON 文本，
        // 避免 Gson 对非基本类型调 asString 抛 UnsupportedOperationException。
        fun str(key: String): String? {
            val v = json.get(key) ?: return null
            if (v.isJsonNull) return null
            return if (v.isJsonPrimitive) v.asString else v.toString()
        }

        fun int(key: String, def: Int = 0): Int {
            val v = json.get(key) ?: return def
            return if (v.isJsonPrimitive) v.asInt else def
        }

        fun double(key: String, def: Double = 0.0): Double {
            val v = json.get(key) ?: return def
            return if (v.isJsonPrimitive) v.asDouble else def
        }

        fun bool(key: String, def: Boolean = false): Boolean {
            val v = json.get(key) ?: return def
            return if (v.isJsonPrimitive) v.asBoolean else def
        }

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
            .applyAccessCode()
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        HttpClientProvider.stream.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                emit(ShellEvent.error(describeHttpError(response.code, body)))
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
    }
        // 网络/读取异常统一转成 error 事件；用 catch 操作符而非 try/catch+emit，
        // 以满足 Flow 异常透明性约束。
        .catch { e ->
            emit(ShellEvent.error("连接失败: ${e.message ?: e.javaClass.simpleName}"))
        }
        .flowOn(Dispatchers.IO)

    /** 探活 GET /health */
    suspend fun health(): Result<String> = withContext(Dispatchers.IO) {        val baseUrl = AppPreferences.baseUrl()
        if (baseUrl.isBlank()) {
            return@withContext Result.failure(Exception("请先配置服务地址"))
        }
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .applyAccessCode()
                .get()
                .build()
            HttpClientProvider.short.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) Result.success(body)
                else Result.failure(Exception(describeHttpError(response.code, body)))
            }
        } catch (e: Exception) {
            Result.failure(Exception("连接失败: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * 带上访问验证码。
     *
     * 服务端（go-shell）配了 API_ACCESS_KEY 时，除 /health 外都必须带对，否则 401；
     * 只认标准 `Authorization: Bearer <code>`，不用 query（验证码不该进地址栏/日志），
     * 与 Python 客户端 dp_client.py 的 ACCESS_CODE 常量一致。没配就不带。
     * 码短的（< 8 位）也照发，让服务端给明确回执，而不是客户端悄悄吞掉。
     */
    private fun Request.Builder.applyAccessCode(): Request.Builder {
        val code = AppPreferences.accessKey.trim()
        if (code.isNotEmpty()) addHeader("Authorization", "Bearer $code")
        return this
    }

    /** 把服务端错误翻成人话，尤其 401（验证码没配/配错）要能一眼看出怎么办 */
    private fun describeHttpError(code: Int, body: String): String {
        if (code != 401) return "HTTP $code: $body"
        val errCode = try {
            JsonParser.parseString(body).asJsonObject.get("code")
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        } catch (_: Exception) {
            ""
        }
        return when (errCode) {
            "access_code_required" -> "服务端要求访问验证码：请在设置 → 服务端连接里填入 API_ACCESS_KEY"
            "access_code_invalid" -> "访问验证码不正确：请核对设置里的验证码与服务端 .env 的 API_ACCESS_KEY"
            else -> "HTTP 401: $body"
        }
    }

    // ---------------- 中途插话 ----------------

    /** 插话结果。服务端的语义见 go-shell README 的「中途插话」。 */
    sealed interface Interject {
        /** 已作为 user 消息插进正在跑的那一轮（模型下一步就能看到） */
        data class Injected(val turnId: String) : Interject
        /** 这一轮没走到工具边界（多卡在长命令上）或已经结束，没赶上 */
        data class NotDelivered(val status: String) : Interject
        /** 当前没有正在跑的轮次（409） */
        data object Idle : Interject
        /** 服务端没有这个接口：Python 版没实现，或地址不对 */
        data object Unsupported : Interject
        data class Failed(val message: String) : Interject
    }

    /**
     * POST /interject：把一句话递给正在跑的那一轮。
     *
     * 服务端不会立刻回 —— 它要等这轮走到工具边界（一批工具结果回来、下一次请求还没发）
     * 才把话插进上下文，所以最长可能等 grace 秒（服务端 INTERJECT_GRACE，默认 20）。
     *
     * @param turnId 本轮 id（来自 SSE 的 turn 事件）；服务端按它找目标轮次
     * @param waitSeconds >0 时覆盖服务端的等待上限
     */
    suspend fun interject(text: String, turnId: String?, waitSeconds: Int = 0): Interject =
        withContext(Dispatchers.IO) {
            val baseUrl = AppPreferences.baseUrl()
            if (baseUrl.isBlank()) return@withContext Interject.Failed("请先配置服务地址")
            if (text.isBlank()) return@withContext Interject.Failed("内容为空")

            val payload = JsonObject().apply {
                addProperty("id", "inj-" + System.currentTimeMillis())
                addProperty("text", text)
                if (!turnId.isNullOrBlank()) addProperty("turn_id", turnId)
                if (waitSeconds > 0) addProperty("wait", waitSeconds)
            }
            val request = Request.Builder()
                .url("$baseUrl/interject")
                .addHeader("Content-Type", "application/json")
                .applyAccessCode()
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            try {
                // 用流式客户端：它读超时无限，扛得住服务端那段"等工具边界"的静默
                HttpClientProvider.stream.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when (response.code) {
                        404 -> Interject.Unsupported
                        409 -> Interject.Idle
                        401 -> Interject.Failed(describeHttpError(401, body))
                        in 200..299 -> {
                            val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                            val status = obj?.get("status")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            val tid = obj?.get("turn_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            if (status == "injected") Interject.Injected(tid)
                            else Interject.NotDelivered(status.ifBlank { "unknown" })
                        }
                        else -> Interject.Failed(describeHttpError(response.code, body))
                    }
                }
            } catch (e: Exception) {
                Interject.Failed("连接失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun parseEvent(lines: List<String>): ShellEvent? {
        val data = lines
            .filter { it.startsWith("data:") }
            .joinToString("\n") { it.removePrefix("data:").trimStart() }
        if (data.isBlank()) return null
        return try {
            val obj = JsonParser.parseString(data).asJsonObject
            val type = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
            ShellEvent(type, obj)
        } catch (_: Exception) {
            null
        }
    }
}
