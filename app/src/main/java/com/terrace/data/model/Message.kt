package com.terrace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单条对话消息（本地镜像）。
 *
 * - role=user  用户问题
 * - role=assistant  模型回复（content 正文；reasoningContent 思考过程；toolCalls 为工具轮）
 * - role=tool  工具执行结果
 */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reasoningContent: String? = null,
    val toolCalls: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)
