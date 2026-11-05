package com.terrace.data

/**
 * 已保存的服务端配置。可保存多个，随时自由切换。
 *
 * [accessKey] 是访问该服务端的验证码（服务端 .env 的 API_ACCESS_KEY，至少 8 位）；
 * 留空表示该服务端不校验。用可空类型是为了兼容早期没有这个字段的已存配置。
 */
data class ServerProfile(
    val name: String,
    val host: String,
    val port: String,
    val accessKey: String? = null
) {
    /** 归一化地址，用于去重与判断是否为当前激活项 */
    val key: String get() = keyOf(host, port)

    /** 归一化后的验证码（空串 = 不校验） */
    val code: String get() = accessKey?.trim().orEmpty()

    /** 是否配置了验证码 */
    val hasCode: Boolean get() = code.isNotEmpty()

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
