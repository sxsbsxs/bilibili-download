package com.sxsbsxs.bilibilidownload.bridge

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.sxsbsxs.bilibilidownload.core.download.AndroidDownloadService
import com.sxsbsxs.bilibilidownload.core.download.DownloadTaskCoordinator
import com.sxsbsxs.bilibilidownload.core.download.DownloadTaskRuntime
import com.sxsbsxs.bilibilidownload.core.model.BangumiInfo
import com.sxsbsxs.bilibilidownload.core.repository.BangumiRepository
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository
import com.sxsbsxs.bilibilidownload.core.parser.BangumiIdParser
import com.sxsbsxs.bilibilidownload.core.security.CookieSession
import com.sxsbsxs.bilibilidownload.core.security.BiliQrLoginClient
import com.sxsbsxs.bilibilidownload.core.security.CookieVault
import com.sxsbsxs.bilibilidownload.core.utils.BiliExportFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Asynchronous bridge. Network and download operations never run on the WebView thread. */
class WebBridge(private val activity: Activity, private val webView: WebView) {
    private val io = Executors.newSingleThreadExecutor()
    private val repository = BiliRepository()
    private val bangumiRepository = BangumiRepository()
    private val downloads = AndroidDownloadService(activity.applicationContext)
    private val taskDownloads = DownloadTaskRuntime.get(activity.applicationContext)
    private val cookieVault = CookieVault(activity.applicationContext)
    private val session = CookieSession().apply { set(cookieVault.load()) }
    private val qrLogin = BiliQrLoginClient()
    private val videos = ConcurrentHashMap<String, com.sxsbsxs.bilibilidownload.core.model.VideoInfo>()
    private val bangumis = ConcurrentHashMap<String, BangumiInfo>()
    private val prefs = activity.getSharedPreferences("download_ids", Activity.MODE_PRIVATE)

    init { taskDownloads.updateSessionCookie(session.get()) }

    @JavascriptInterface
    fun parseVideo(raw: String, requestId: String) {
        io.execute {
            try {
                if (BangumiIdParser.parse(raw) != null) {
                    val info = bangumiRepository.parse(raw, session.get())
                    val token = "bangumi:" + info.seasonId + ":" + System.nanoTime()
                    bangumis[token] = info
                    val episodes = JSONArray().apply {
                        info.episodes.forEach { ep ->
                            put(JSONObject().put("epId", ep.epId).put("aid", ep.aid).put("bvid", ep.bvid)
                                .put("cid", ep.cid).put("title", ep.title).put("longTitle", ep.longTitle)
                                .put("duration", ep.duration).put("cover", ep.cover).put("seasonId", ep.seasonId))
                        }
                    }
                    respond(requestId, JSONObject().put("ok", true).put("kind", "bangumi").put("token", token)
                        .put("bangumi", JSONObject().put("seasonId", info.seasonId).put("title", info.title)
                            .put("cover", info.cover).put("selectedEpisodeId", info.selectedEpisodeId ?: 0L)
                            .put("episodes", episodes)))
                    return@execute
                }
                val video = repository.video(raw)
                val token = video.bvid + ":" + video.cid + ":" + System.nanoTime()
                videos[token] = video
                val pages = JSONArray().apply {
                    video.pages.forEach { p ->
                        put(JSONObject().put("cid", p.cid).put("page", p.page).put("title", p.title).put("duration", p.duration))
                    }
                }
                val result = JSONObject().put("ok", true).put("kind", "video").put("token", token)
                    .put("video", JSONObject().put("aid", video.aid).put("bvid", video.bvid)
                        .put("cid", video.cid).put("title", video.title).put("description", video.description)
                        .put("duration", video.duration).put("owner", video.owner).put("cover", video.cover).put("pages", pages))
                try {
                    val data = repository.dashStreams(video, null, session.get())
                    val rows = JSONArray().apply {
                        data.first.forEach { q -> put(JSONObject().put("quality", q.quality).put("description", q.description).put("available", q.available)) }
                    }
                    result.put("qualities", rows).put("defaultQuality", data.second.quality)
                        .put("streamMessage", if (data.second.isDash) "已识别 DASH 独立音视频流" else "该视频返回渐进式 MP4 流")
                        .put("streamMode", if (data.second.isDash) "dash" else "progressive")
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
    fun getBangumiQualities(token: String, epId: Long, requestId: String) {
        io.execute {
            try {
                val info = bangumis[token] ?: throw IllegalStateException("番剧信息已过期，请重新解析")
                val episode = info.episodes.firstOrNull { it.epId == epId } ?: throw IllegalArgumentException("找不到该剧集")
                val data = repository.dashStreams(episode.asVideoInfo(), null, session.get())
                val rows = JSONArray().apply {
                    data.first.forEach { q -> put(JSONObject().put("quality", q.quality).put("description", q.description)) }
                }
                respond(requestId, JSONObject().put("ok", true).put("qualities", rows)
                    .put("defaultQuality", data.second.quality).put("streamMessage", data.second.description))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "读取剧集清晰度失败"))
            }
        }
    }

    @JavascriptInterface
    fun downloadEpisodes(token: String, selectionJson: String, requestId: String) {
        io.execute {
            try {
                val info = bangumis[token] ?: throw IllegalStateException("番剧信息已过期，请重新解析")
                val selected = JSONArray(selectionJson)
                if (selected.length() == 0) throw IllegalArgumentException("请至少选择一集")
                val queued = JSONArray()
                val failed = JSONArray()
                for (i in 0 until selected.length()) {
                    val row = selected.optJSONObject(i) ?: continue
                    val epId = row.optLong("epId")
                    val requestedQuality = row.optInt("quality", 0)
                    val episode = info.episodes.firstOrNull { it.epId == epId }
                    if (episode == null) {
                        failed.put(JSONObject().put("epId", epId).put("error", "找不到该剧集"))
                        continue
                    }
                    try {
                        val video = episode.asVideoInfo()
                        val stream = if (requestedQuality > 0) {
                            repository.dashStreamAtQuality(video, requestedQuality, null, session.get())
                        } else {
                            repository.dashStreams(video, null, session.get()).second
                        }
                        val title = info.title + " " + episode.title + " " + episode.longTitle
                        val id = taskDownloads.enqueue(stream, title, session.get(), episode.bvid, episode.cid, stream.quality)
                        queued.put(JSONObject().put("epId", epId).put("id", id).put("quality", stream.quality))
                    } catch (e: Exception) {
                        failed.put(JSONObject().put("epId", epId).put("title", episode.title)
                            .put("error", e.message ?: "创建下载任务失败"))
                    }
                }
                if (queued.length() > 0) startDownloadService()
                respond(requestId, JSONObject().put("ok", queued.length() > 0).put("queued", queued)
                    .put("failed", failed).put("queuedCount", queued.length()).put("failedCount", failed.length())
                    .put("error", if (queued.length() == 0) "所选剧集均未能加入下载队列" else ""))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "批量下载失败"))
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
                val id = taskDownloads.enqueue(stream, title, session.get(), video.bvid, cid, stream.quality)
                startDownloadService()
                respond(requestId, JSONObject().put("ok", true).put("id", id).put("quality", stream.quality)
                    .put("description", stream.description).put("mode", if (stream.isDash) "dash" else "progressive"))
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
                    .put("defaultQuality", data.second.quality)
                    .put("streamMessage", if (data.second.isDash) "当前分 P 返回 DASH 音视频流" else "当前分 P 返回渐进式 MP4 流"))
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
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "获取字幕失败"))
            }
        }
    }

    @JavascriptInterface
    fun exportSubtitle(url: String, title: String, requestId: String) {
        io.execute {
            try {
                val raw = repository.fetchText(url, session.get())
                val srt = BiliExportFormatter.subtitleSrt(raw)
                val uri = downloads.saveText(srt, title + "_subtitle", "application/x-subrip", "srt")
                respond(requestId, JSONObject().put("ok", true).put("uri", uri).put("format", "srt"))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "字幕导出失败"))
            }
        }
    }

    @JavascriptInterface
    fun exportDanmaku(cid: Long, title: String, requestId: String) {
        io.execute {
            try {
                val raw = repository.fetchText(repository.danmakuUrl(cid), session.get())
                val csv = BiliExportFormatter.danmakuCsv(raw)
                val uri = downloads.saveText(csv, title + "_danmaku", "text/csv", "csv")
                respond(requestId, JSONObject().put("ok", true).put("uri", uri).put("format", "csv"))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "弹幕导出失败"))
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
                    (downloads.query(ids) + taskDownloads.query()).forEach { row ->
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
            if (id < 0L) taskDownloads.remove(id) else downloads.remove(id)
            respond(requestId, JSONObject().put("ok", true))
        }
    }

    @JavascriptInterface
    fun pauseDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) taskDownloads.pause(id) else false
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内暂停"))
        }
    }

    @JavascriptInterface
    fun resumeDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) taskDownloads.resume(id) else false
            if (ok) startDownloadService()
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内恢复"))
        }
    }

    @JavascriptInterface
    fun retryDownload(id: Long, requestId: String) {
        io.execute {
            val ok = if (id < 0L) taskDownloads.retry(id) else false
            if (ok) startDownloadService()
            respond(requestId, JSONObject().put("ok", ok).put("error", if (ok) "" else "该任务暂不支持应用内重试"))
        }
    }

    @JavascriptInterface
    fun createQrLogin(requestId: String) {
        io.execute {
            try {
                val ticket = qrLogin.create()
                respond(requestId, JSONObject().put("ok", true).put("key", ticket.key).put("image", ticket.imageDataUrl))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "生成登录二维码失败"))
            }
        }
    }

    @JavascriptInterface
    fun pollQrLogin(key: String, requestId: String) {
        io.execute {
            try {
                val result = qrLogin.poll(key)
                if (result.state == "success") { session.set(result.cookie); cookieVault.save(result.cookie); taskDownloads.updateSessionCookie(result.cookie) }
                respond(requestId, JSONObject().put("ok", true).put("state", result.state)
                    .put("message", result.message).put("loggedIn", session.isLoggedIn))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "查询扫码状态失败"))
            }
        }
    }

    @JavascriptInterface
    fun bulkTaskAction(idsJson: String, action: String, requestId: String) {
        io.execute {
            try {
                val ids = JSONArray(idsJson)
                var succeeded = 0
                var skipped = 0
                for (i in 0 until ids.length()) {
                    val id = ids.optLong(i)
                    val ok = when (action) {
                        "pause" -> id < 0L && taskDownloads.pause(id)
                        "resume" -> id < 0L && taskDownloads.resume(id)
                        "retry" -> id < 0L && taskDownloads.retry(id)
                        "remove" -> if (id < 0L) { taskDownloads.remove(id); true } else { downloads.remove(id); true }
                        else -> false
                    }
                    if (ok) succeeded++ else skipped++
                }
                if ((action == "resume" || action == "retry") && succeeded > 0) startDownloadService()
                respond(requestId, JSONObject().put("ok", true).put("succeeded", succeeded).put("skipped", skipped))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", false).put("error", e.message ?: "批量操作失败"))
            }
        }
    }

    @JavascriptInterface
    fun setCookie(cookie: String, requestId: String) {
        session.set(cookie)
        cookieVault.save(cookie)
        taskDownloads.updateSessionCookie(cookie)
        respond(requestId, JSONObject().put("ok", true).put("loggedIn", session.isLoggedIn))
    }

    @JavascriptInterface
    fun accountStatus(requestId: String) {
        io.execute {
            try {
                if (!session.isLoggedIn) {
                    respond(requestId, JSONObject().put("ok", true).put("loggedIn", false))
                    return@execute
                }
                val profile = repository.accountProfile(session.get().orEmpty())
                val loggedIn = profile.optBoolean("isLogin", true)
                if (!loggedIn) {
                    session.clear()
                    cookieVault.clear()
                    taskDownloads.updateSessionCookie(null)
                }
                respond(requestId, JSONObject().put("ok", true).put("loggedIn", loggedIn)
                    .put("name", profile.optString("uname")).put("avatar", profile.optString("face"))
                    .put("mid", profile.optLong("mid")))
            } catch (e: Exception) {
                respond(requestId, JSONObject().put("ok", true).put("loggedIn", session.isLoggedIn)
                    .put("error", e.message ?: "读取账号信息失败"))
            }
        }
    }

    private fun startDownloadService() {
        val intent = Intent(activity, com.sxsbsxs.bilibilidownload.DownloadForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.startForegroundService(intent) else activity.startService(intent)
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
        bangumis.clear()
        session.clear()
    }
}
