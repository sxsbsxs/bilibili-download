package com.sxsbsxs.bilibilidownload.bridge

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.sxsbsxs.bilibilidownload.core.download.AndroidDownloadService
import com.sxsbsxs.bilibilidownload.core.download.DashDownloadCoordinator
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
    private val dashDownloads = DashDownloadCoordinator(activity.applicationContext)
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
                    val data = repository.dashStreams(video, null, session.get())
                    val rows = JSONArray().apply {
                        data.first.forEach { q -> put(JSONObject().put("quality", q.quality).put("description", q.description).put("available", q.available)) }
                    }
                    result.put("qualities", rows).put("defaultQuality", data.second.quality).put("streamMessage", if (data.second.isDash) "已识别 DASH 独立音视频流" else "该视频返回渐进式 MP4 流").put("streamMode", if (data.second.isDash) "dash" else "progressive")
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
                val stream = repository.dashStreamAtQuality(video, quality, page, session.get())
                if (stream.isDash) {
                    val id = dashDownloads.enqueue(stream, title, session.get())
                    respond(requestId, JSONObject().put("ok", true).put("id", id).put("quality", stream.quality).put("description", stream.description).put("mode", "dash"))
                } else {
                    val id = downloads.enqueue(stream.videoUrl, title)
                    rememberDownload(id)
                    respond(requestId, JSONObject().put("ok", true).put("id", id).put("quality", stream.quality).put("description", stream.description).put("mode", "progressive"))
                }
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "无法创建下载任务"))
            }
        }
    }


    @JavascriptInterface
    fun getQualities(token: String, cid: Long, requestId: String) {
        io.execute {
            try {
                val video = videos[token] ?: throw IllegalStateException("视频信息已过期，请重新解析")
                val page = video.pages.firstOrNull { it.cid == cid }
                val data = repository.dashStreams(video, page, session.get())
                val rows = JSONArray().apply {
                    data.first.forEach { q -> put(JSONObject().put("quality", q.quality).put("description", q.description)) }
                }
                respond(requestId, JSONObject().put("ok", true).put("qualities", rows)
                    .put("defaultQuality", data.second.quality).put("streamMessage", if (data.second.isDash) "当前分 P 返回 DASH 音视频流" else "当前分 P 返回渐进式 MP4 流"))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "清晰度读取失败"))
            }
        }
    }

    @JavascriptInterface
    fun getSubtitles(token: String, cid: Long, requestId: String) {
        io.execute {
            try {
                val video = videos[token] ?: throw IllegalStateException("视频信息已过期，请重新解析")
                val page = video.pages.firstOrNull { it.cid == cid }
                val rows = JSONArray().apply {
                    repository.subtitles(video, page, session.get()).forEach { item ->
                        put(JSONObject().put("language", item.language).put("url", item.url).put("format", item.format))
                    }
                }
                respond(requestId, JSONObject().put("ok", true).put("subtitles", rows))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "字幕读取失败"))
            }
        }
    }

    @JavascriptInterface
    fun exportSubtitle(url: String, title: String, requestId: String) {
        io.execute {
            try {
                if (!url.startsWith("https://")) throw IllegalArgumentException("字幕地址不安全")
                val id = downloads.enqueue(url, title + "_subtitle", "application/json", "json")
                rememberDownload(id)
                respond(requestId, JSONObject().put("ok", true).put("id", id))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "字幕下载失败"))
            }
        }
    }

    @JavascriptInterface
    fun exportDanmaku(cid: Long, title: String, requestId: String) {
        io.execute {
            try {
                val id = downloads.enqueue(repository.danmakuUrl(cid), title + "_danmaku", "application/xml", "xml")
                rememberDownload(id)
                respond(requestId, JSONObject().put("ok", true).put("id", id))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "弹幕下载失败"))
            }
        }
    }

    private fun rememberDownload(id: Long) {
        val ids = prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        ids.add(id.toString())
        prefs.edit().putStringSet("ids", ids).apply()
    }

    @JavascriptInterface
    fun getDownloads(requestId: String) {
        io.execute {
            try {
                val ids = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
                val rows = JSONArray().apply {
                    (downloads.query(ids) + dashDownloads.query()).forEach { row ->
                        put(JSONObject().put("id", row.id).put("title", row.title).put("status", row.status)
                            .put("downloadedBytes", row.downloadedBytes).put("totalBytes", row.totalBytes).put("error", row.error))
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
            if (id < 0L) dashDownloads.remove(id) else downloads.remove(id)
            respond(requestId, JSONObject().put("ok", true))
        }
    }

    @JavascriptInterface
    fun pauseDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) dashDownloads.pause(id) else false
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内暂停"))
        }
    }

    @JavascriptInterface
    fun resumeDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) dashDownloads.resume(id) else false
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内恢复"))
        }
    }

    @JavascriptInterface
    fun retryDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) dashDownloads.retry(id) else false
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内重试"))
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
        dashDownloads.close()
        videos.clear()
        session.clear()
    }
}
