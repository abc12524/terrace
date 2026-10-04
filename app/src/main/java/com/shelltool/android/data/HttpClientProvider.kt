package com.shelltool.android.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 统一 OkHttpClient：普通请求（健康检查）用短超时；
 * 流式对话用无限读超时（服务端每 15s 发心跳保活）。
 */
object HttpClientProvider {

    val short: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val stream: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
