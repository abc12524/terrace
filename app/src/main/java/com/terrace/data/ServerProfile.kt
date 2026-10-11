package com.terrace.data

/**
 * 已保存的服务端配置。可保存多个，随时自由切换。
 */
data class ServerProfile(
    val name: String,
    val host: String,
    val port: String
) {
    /** 归一化地址，用于去重与判断是否为当前激活项 */
    val key: String get() = keyOf(host, port)

    /** 展示用地址，例如 http://192.168.1.10:8000 */
    val display: String
        get() {
            val h = host.trim().removeSuffix("/")
            if (h.isBlank()) return ""
            val s = if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
            return "$s:${port.trim()}"
        }

    companion object {
        fun keyOf(host: String, port: String): String {
            val h = host.trim().removeSuffix("/")
                .removePrefix("https://").removePrefix("http://")
            return "${h.lowercase()}:${port.trim()}"
        }
    }
}
