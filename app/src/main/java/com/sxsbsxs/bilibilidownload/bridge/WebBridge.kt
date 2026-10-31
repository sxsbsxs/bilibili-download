package com.sxsbsxs.bilibilidownload.bridge

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.sxsbsxs.bilibilidownload.core.download.AndroidDownloadService
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository
import com.sxsbsxs.bilibilidownload.core.security.CookieSession
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Asynchronous bridge. Network and download operations never run on the WebView thread. */
class WebBridge(private val activity: Activity, private val webView: WebView) {
    private val io = Executors.newSingleThreadExecutor()
    private val repository = BiliRepository()
    private val downloads = AndroidDownloadService(activity.applicationContext)
    private val session = CookieSession()
    private val videos = ConcurrentHashMap<String, com.sxsbsxs.bilibilidownload.core.model.VideoInfo>()
    private val prefs = activity.getSharedPreferences("download_ids", Activity.MODE_PRIVATE)

    @JavascriptInterface
    fun parseVideo(raw: String, requestId: String) {
        io.execute {
            try {
                val video = repository.video(raw)
                val token = video.bvid + ":" + video.cid + ":" + System.nanoTime()
                videos[token] = video
                val pages = JSONArray().apply {
                    video.pages.forEach { p ->
                        put(JSONObject().put("cid", p.cid).put("page", p.page).put("title", p.title).put("duration", p.duration))
                    }
                }
                val result = JSONObject().put("ok", true).put("token", token)
                    .put("video", JSONObject().put("aid", video.aid).put("bvid", video.bvid)
                        .put("cid", video.cid).put("title", video.title).put("description", video.description)
                        .put("duration", video.duration).put("owner", video.owner).put("cover", video.cover).put("pages", pages))
                try {
                    val data = repository.streams(video, null, session.get())
                    val rows = JSONArray().apply {
                        data.first.forEach { q -> put(JSONObject().put("quality", q.quality).put("description", q.description).put("available", q.available)) }
                    }
                    result.put("qualities", rows).put("defaultQuality", data.second.quality).put("streamMessage", "")
                } catch (e: Exception) {
                    result.put("qualities", JSONArray()).put("defaultQuality", 0).put("streamMessage", e.message ?: "暂时无法获取视频流")
                }
                respond(requestId, result)
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "解析失败"))
            }
        }
    }

    @JavascriptInterface
    fun download(token: String, cid: Long, quality: Int, title: String, requestId: String) {
        io.execute {
            try {
                val video = videos[token] ?: throw IllegalStateException("视频信息已过期，请重新解析")
                val page = video.pages.firstOrNull { it.cid == cid }
                val stream = repository.streamAtQuality(video, quality, page, session.get())
                val id = downloads.enqueue(stream.url, title)
                val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
                ids.add(id.toString())
                prefs.edit().putStringSet("ids", ids).apply()
                respond(requestId, JSONObject().put("ok", true).put("id", id).put("quality", stream.quality).put("description", stream.description))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "无法创建下载任务"))
            }
        }
    }

    @JavascriptInterface
    fun getDownloads(requestId: String) {
        io.execute {
            try {
                val ids = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
                val rows = JSONArray().apply {
                    downloads.query(ids).forEach { row ->
                        put(JSONObject().put("id", row.id).put("title", row.title).put("status", row.status)
                            .put("downloadedBytes", row.downloadedBytes).put("totalBytes", row.totalBytes))
                    }
                }
                respond(requestId, JSONObject().put("ok", true).put("tasks", rows))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "读取下载任务失败"))
            }
        }
    }

    @JavascriptInterface
    fun cancelDownload(id: Long, requestId: String) {
        io.execute {
            downloads.remove(id)
            respond(requestId, JSONObject().put("ok", true))
        }
    }

    @JavascriptInterface
    fun setCookie(cookie: String, requestId: String) {
        session.set(cookie)
        respond(requestId, JSONObject().put("ok", true).put("loggedIn", session.isLoggedIn))
    }

    @JavascriptInterface
    fun accountStatus(requestId: String) {
        respond(requestId, JSONObject().put("ok", true).put("loggedIn", session.isLoggedIn))
    }

    private fun respond(requestId: String, value: JSONObject) {
        val safeId = JSONObject.quote(requestId)
        val safeValue = JSONObject.quote(value.toString())
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                webView.evaluateJavascript("window.NativeBridge && window.NativeBridge.onResult($safeId, $safeValue)", null)
            }
        }
    }

    fun close() {
        io.shutdownNow()
        videos.clear()
        session.clear()
    }
}
