package com.terrace.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 本地配置。保存当前服务端 host / port，以及可自由切换的已保存服务端列表。
 */
object AppPreferences {
    private const val PREFS_NAME = "shell_tool_prefs"
    private const val KEY_SERVERS = "servers"
    private lateinit var prefs: SharedPreferences

    private val gson = Gson()
    private val serverListType = object : TypeToken<List<ServerProfile>>() {}.type

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

    // ---------------- 已保存的服务端（支持自由切换） ----------------

    /** 当前激活服务端的归一化标识 */
    fun activeKey(): String = ServerProfile.keyOf(host, port)

    fun isActive(profile: ServerProfile): Boolean = profile.key == activeKey()

    fun servers(): List<ServerProfile> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<ServerProfile>>(raw, serverListType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<ServerProfile>) {
        prefs.edit().putString(KEY_SERVERS, gson.toJson(list)).apply()
    }

    /** 保存（新增或更新）一个服务端，地址相同视为同一项；随后设为当前连接 */
    fun saveServer(profile: ServerProfile) {
        val cleaned = ServerProfile(profile.name.trim(), profile.host.trim(), profile.port.trim())
        if (cleaned.host.isBlank()) return
        val list = servers().toMutableList()
        val idx = list.indexOfFirst { it.key == cleaned.key }
        if (idx >= 0) list[idx] = cleaned else list.add(cleaned)
        persist(list)
        switchTo(cleaned)
    }

    fun removeServer(profile: ServerProfile) {
        persist(servers().filterNot { it.key == profile.key })
    }

    /** 自由切换：把当前 host / port 指向指定服务端 */
    fun switchTo(profile: ServerProfile) {
        host = profile.host
        port = profile.port
    }
}
