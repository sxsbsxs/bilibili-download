package com.sxsbsxs.bilibilidownload.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BiliHttpClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true).build()

    fun getJson(url: String, cookie: String? = null): JSONObject {
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://www.bilibili.com/")
        if (!cookie.isNullOrBlank()) builder.header("Cookie", cookie)
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("网络请求失败：HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: throw IOException("B站返回空数据"))
        }
    }
}
