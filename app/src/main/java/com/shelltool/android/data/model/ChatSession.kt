package com.shelltool.android.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地对话会话。对话历史仅存本地 SQLite，用于在安卓端查看。
 */
@Entity(tableName = "sessions")
data class ChatSession(
    @PrimaryKey
    val id: String,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    /** 首条消息是否已用 -n 通知服务端新建会话 */
    val serverStarted: Boolean = false,
    // 服务端 usage 事件累计的用量
    val totalHit: Int = 0,
    val totalMiss: Int = 0,
    val totalOut: Int = 0,
    val totalCost: Double = 0.0,
    val model: String = "",
    val balance: String = "",
    val symbol: String = "¥"
)
