package com.sxsbsxs.bilibilidownload.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BiliHttpClient {
    private val storedCookies = ConcurrentHashMap<String, Cookie>()
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                val key = cookie.domain + "|" + cookie.name
                if (cookie.expiresAt <= System.currentTimeMillis()) storedCookies.remove(key)
                else storedCookies[key] = cookie
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            storedCookies.entries.removeIf { it.value.expiresAt <= now }
            return storedCookies.values.filter { it.matches(url) }
        }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun ensureWebCookies(cookie: String? = null) {
        if (cookieJar.loadForRequest("https://www.bilibili.com/".toHttpUrl()).none { it.name == "buvid3" }) {
            getText("https://www.bilibili.com/", cookie)
        }
    }

    fun getJson(url: String, cookie: String? = null): JSONObject = JSONObject(getText(url, cookie))

    fun getText(url: String, cookie: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .header("Referer", "https://www.bilibili.com/")
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                val merged = linkedMapOf<String, String>()
                cookieJar.loadForRequest(url.toHttpUrl()).forEach { merged[it.name] = it.value }
                cookie.orEmpty().split(';').forEach { part ->
                    val index = part.indexOf('=')
                    if (index > 0) merged[part.substring(0, index).trim()] = part.substring(index + 1).trim()
                }
                if (merged.isNotEmpty()) header("Cookie", merged.entries.joinToString("; ") { it.key + "=" + it.value })
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("网络请求失败：HTTP " + response.code)
            return response.body?.string() ?: throw IOException("B站返回空数据")
        }
    }

    private fun String.toHttpUrl(): HttpUrl = HttpUrl.get(this)
}
