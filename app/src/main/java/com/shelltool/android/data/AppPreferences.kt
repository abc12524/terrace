package com.shelltool.android.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地配置。只保存 shell-tool 服务端的 host / port。
 */
object AppPreferences {
    private const val PREFS_NAME = "shell_tool_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = prefs.edit().putString("host", value.trim()).apply()

    var port: String
        get() = prefs.getString("port", "8000") ?: "8000"
        set(value) = prefs.edit().putString("port", value.trim()).apply()

    /** 拼接出的服务地址，例如 http://192.168.1.10:8000 */
    fun baseUrl(): String {
        val h = host.trim().removeSuffix("/")
        if (h.isBlank()) return ""
        val withScheme = if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
        return "$withScheme:${port.trim()}"
    }
}
