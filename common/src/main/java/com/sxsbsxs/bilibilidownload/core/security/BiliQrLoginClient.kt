package com.sxsbsxs.bilibilidownload.core.security

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Bilibili web QR login. The QR payload is rendered locally; it is never sent to a third-party QR service. */
class BiliQrLoginClient {
    data class QrTicket(val key: String, val imageDataUrl: String, val url: String)
    data class PollResult(val state: String, val message: String, val cookie: String = "")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun create(): QrTicket {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("获取登录二维码失败：HTTP ${response.code}")
            val root = JSONObject(response.body?.string() ?: throw IOException("B站返回空二维码数据"))
            if (root.optInt("code", -1) != 0) throw IOException(root.optString("message", "获取登录二维码失败"))
            val data = root.getJSONObject("data")
            val key = data.optString("qrcode_key")
            val url = data.optString("url")
            if (key.isBlank() || url.isBlank()) throw IOException("B站没有返回有效二维码")
            return QrTicket(key, renderQr(url), url)
        }
    }

    fun poll(key: String): PollResult {
        if (key.isBlank()) throw IllegalArgumentException("二维码凭据为空")
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + java.net.URLEncoder.encode(key, "UTF-8"))
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("查询扫码状态失败：HTTP ${response.code}")
            val root = JSONObject(response.body?.string() ?: throw IOException("B站返回空扫码状态"))
            val data = root.optJSONObject("data") ?: throw IOException(root.optString("message", "扫码状态数据为空"))
            return when (data.optInt("code", -1)) {
                0 -> {
                    val cookieParts = response.headers.values("Set-Cookie").mapNotNull { header ->
                        header.substringBefore(";").takeIf { it.contains("=") && !it.startsWith("=") }
                    }.toMutableList()
                    val loginUri = Uri.parse(data.optString("url"))
                    for (name in listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")) {
                        val value = loginUri.getQueryParameter(name)
                        if (!value.isNullOrBlank()) cookieParts.add(name + "=" + value)
                    }
                    val cookie = cookieParts.distinctBy { it.substringBefore("=") }.joinToString("; ")
                    if (cookieParts.none { it.startsWith("SESSDATA=") }) throw IOException("登录已确认，但未取得 SESSDATA；请重新扫码")
                    PollResult("success", "登录成功", cookie)
                }
                86101 -> PollResult("waiting", "等待使用 B 站 APP 扫码")
                86090 -> PollResult("scanned", "已扫码，请在 B 站 APP 确认登录")
                86038 -> PollResult("expired", "二维码已过期，请刷新")
                else -> PollResult("error", data.optString("message", root.optString("message", "扫码登录失败")))
            }
        }
    }

    private fun renderQr(value: String): String {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        for (y in 0 until 640) for (x in 0 until 640) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        return "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
    }
}
