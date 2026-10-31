package com.sxsbsxs.bilibilidownload.core.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.sxsbsxs.bilibilidownload.core.model.DashStreamInfo
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-owned media queue. Partial files and HTTP validators survive process death.
 * Cookies are memory-only; signed URLs and task metadata are encrypted at rest.
 */
class DownloadTaskCoordinator(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("dash_download_tasks_v2", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(true).retryOnConnectionFailure(true).build()
    private val executor = Executors.newFixedThreadPool(2)
    private val sequence = AtomicLong()
    private val tasks = ConcurrentHashMap<Long, Task>()

    private data class Task(
        val id: Long,
        val title: String,
        @Volatile var videoUrl: String,
        @Volatile var audioUrl: String?,
        @Volatile var status: Int = 1,
        @Volatile var downloaded: Long = 0,
        @Volatile var total: Long = 0,
        @Volatile var error: String = "",
        @Volatile var paused: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var cookie: String? = null,
        @Volatile var call: Call? = null,
        @Volatile var generation: Long = 0,
        @Volatile var running: Boolean = false,
        val bvid: String = "",
        val cid: Long = 0,
        val quality: Int = 0,
        @Volatile var videoEtag: String = "",
        @Volatile var audioEtag: String = "",
        @Volatile var videoModified: String = "",
        @Volatile var audioModified: String = "",
        @Volatile var videoTotal: Long = 0,
        @Volatile var audioTotal: Long = 0
    )

    private class ExpiredStreamException(message: String) : IOException(message)

    init {
        restoreTasks()
        tasks.values.filter { it.status == 1 || it.status == 2 || it.status == 3 }.forEach { task ->
            task.status = 1
            persist()
            schedule(task)
        }
    }

    @Synchronized
    fun enqueue(
        stream: DashStreamInfo,
        title: String,
        cookie: String?,
        bvid: String = "",
        cid: Long = 0,
        quality: Int = stream.quality
    ): Long {
        require(stream.videoUrl.isNotBlank()) { "视频流地址为空" }
        val id = -(System.currentTimeMillis() * 1000L + sequence.incrementAndGet())
        val task = Task(id, safeName(title), stream.videoUrl, stream.audioUrl,
            cookie = cookie, bvid = bvid, cid = cid, quality = quality)
        tasks[id] = task
        persist()
        schedule(task)
        return id
    }

    @Synchronized
    fun updateSessionCookie(cookie: String?) {
        tasks.values.forEach { it.cookie = cookie }
    }

    fun query(): List<DownloadRecord> = tasks.values.sortedByDescending { it.id }.map {
        DownloadRecord(it.id, it.title, it.status, it.downloaded, it.total, it.error)
    }

    @Synchronized
    fun pause(id: Long): Boolean {
        val task = tasks[id] ?: return false
        if (task.status != 1 && task.status != 2) return false
        task.paused = true
        task.generation++
        task.status = 4
        task.call?.cancel()
        persist()
        return true
    }

    @Synchronized
    fun resume(id: Long): Boolean {
        val task = tasks[id] ?: return false
        if (task.status != 4 && task.status != 16) return false
        task.paused = false
        task.generation++
        task.cancelled = false
        task.error = ""
        task.status = 1
        persist()
        schedule(task)
        return true
    }

    fun retry(id: Long): Boolean = resume(id)

    @Synchronized
    fun remove(id: Long) {
        val task = tasks[id] ?: return
        task.cancelled = true
        task.call?.cancel()
        tasks.remove(id)
        File(app.cacheDir, workName(id)).deleteRecursively()
        persist()
    }

    fun close() {
        tasks.values.forEach { task ->
            task.generation++
            task.paused = true
            if (task.status == 2 || task.status == 3) task.status = 1
            task.call?.cancel()
        }
        persist()
        executor.shutdownNow()
    }

    private fun schedule(task: Task) {
        synchronized(task) {
            if (task.running || task.cancelled || task.paused) return
            task.running = true
        }
        executor.execute { runTask(task, task.generation) }
    }

    private fun runTask(task: Task, generation: Long) {
        task.status = 2
        persist()
        val work = File(app.cacheDir, workName(task.id))
        try {
            work.mkdirs()
            val video = File(work, "video.m4s")
            val audio = File(work, "audio.m4s")
            val output = File(work, "merged.mp4")
            task.downloaded = video.length() + if (task.audioUrl.isNullOrBlank()) 0L else audio.length()
            task.total = task.videoTotal + task.audioTotal
            persist()

            var completed = false
            for (refreshAttempt in 0..1) {
                try {
                    downloadWithRetries(task.videoUrl, video, task, generation, "video")
                    checkActive(task, generation)
                    if (!task.audioUrl.isNullOrBlank()) {
                        downloadWithRetries(task.audioUrl!!, audio, task, generation, "audio")
                        checkActive(task, generation)
                        task.status = 3
                        persist()
                        mux(video, audio, output)
                    } else {
                        video.copyTo(output, overwrite = true)
                    }
                    completed = true
                    break
                } catch (expired: ExpiredStreamException) {
                    if (refreshAttempt != 0) throw expired
                    refreshExpiredStream(task, video, audio, generation)
                }
            }
            if (!completed) throw IOException("下载流地址刷新后仍无法完成下载")
            checkActive(task, generation)
            publish(output, task.title, task.bvid, task.quality)
            task.downloaded = output.length()
            task.total = output.length()
            task.status = 8
            task.error = ""
            persist()
        } catch (e: Exception) {
            if (generation == task.generation) when {
                task.cancelled -> Unit
                task.paused -> {
                    task.status = 4
                    task.error = ""
                }
                else -> {
                    task.status = 16
                    task.error = e.message ?: "媒体下载失败"
                }
            }
            persist()
        } finally {
            task.call = null
            if (task.status == 8 || task.cancelled) {
                if (task.status == 8 && app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getBoolean("keep_source_tracks", false)) {
                    File(work, "merged.mp4").delete()
                } else {
                    work.deleteRecursively()
                }
            }
            synchronized(task) { task.running = false }
            if (task.status == 1 && !task.cancelled && !task.paused) schedule(task)
        }
    }

    private fun downloadWithRetries(url: String, target: File, task: Task, generation: Long, part: String) {
        var attempt = 0
        while (true) {
            checkActive(task, generation)
            try {
                download(url, target, task, generation, part)
                return
            } catch (e: ExpiredStreamException) {
                throw e
            } catch (e: IOException) {
                checkActive(task, generation)
                if (attempt >= 3) throw e
                val delay = 1000L shl attempt
                attempt++
                Thread.sleep(delay)
            }
        }
    }

    private fun download(
        url: String,
        target: File,
        task: Task,
        generation: Long,
        part: String,
        allowRangeReset: Boolean = true
    ) {
        checkActive(task, generation)
        val offset = if (target.exists()) target.length() else 0L
        val validator = if (part == "video") task.videoEtag else task.audioEtag
        val modified = if (part == "video") task.videoModified else task.audioModified
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://www.bilibili.com/")
            .apply {
                if (offset > 0) {
                    header("Range", "bytes=$offset-")
                    when {
                        validator.isNotBlank() -> header("If-Range", validator)
                        modified.isNotBlank() -> header("If-Range", modified)
                    }
                }
                if (!task.cookie.isNullOrBlank()) header("Cookie", task.cookie!!)
            }.build()
        val call = client.newCall(request)
        task.call = call
        var restartFromZero = false
        call.execute().use { response ->
            if (response.code == 401 || response.code == 403 || response.code == 404 || response.code == 410) {
                throw ExpiredStreamException("B站视频直链已过期（HTTP ${response.code}），正在尝试重新获取")
            }
            if (response.code == 416 && offset > 0 && allowRangeReset) {
                restartFromZero = true
                return@use
            }
            if (response.code != 200 && response.code != 206) {
                throw IOException("下载媒体流失败：HTTP " + response.code)
            }
            val responseEtag = response.header("ETag").orEmpty()
            val responseModified = response.header("Last-Modified").orEmpty()
            val append = offset > 0 && response.code == 206
            if (append) {
                val range = response.header("Content-Range").orEmpty()
                val start = Regex("bytes\\s+(\\d+)-").find(range)?.groupValues?.get(1)?.toLongOrNull()
                val identityChanged = (validator.isNotBlank() && validator != responseEtag) ||
                    (validator.isBlank() && modified.isNotBlank() && modified != responseModified)
                if (start != offset || identityChanged) {
                    restartFromZero = true
                    return@use
                }
            }
            val body = response.body ?: throw IOException("B站返回空媒体流")
            if (offset > 0 && !append) {
                target.delete()
                task.downloaded = (task.downloaded - offset).coerceAtLeast(0L)
            }
            if (responseEtag.isNotBlank()) setPartEtag(task, part, responseEtag)
            if (responseModified.isNotBlank()) setPartModified(task, part, responseModified)
            val base = if (append) offset else 0L
            val rangeTotal = Regex("bytes\\s+\\d+-\\d+/(\\d+)").find(response.header("Content-Range").orEmpty())
                ?.groupValues?.get(1)?.toLongOrNull()
            val partTotal = rangeTotal ?: body.contentLength().takeIf { it >= 0L }?.plus(base) ?: 0L
            if (partTotal > 0) setPartTotal(task, part, partTotal)
            task.total = task.videoTotal + task.audioTotal
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastPersisted = task.downloaded
                    while (true) {
                        checkActive(task, generation)
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        task.downloaded += n
                        if (task.downloaded - lastPersisted >= 1024 * 1024) {
                            lastPersisted = task.downloaded
                            persist()
                        }
                    }
                    output.fd.sync()
                }
            }
        }
        task.call = null
        if (restartFromZero) {
            if (!allowRangeReset) throw IOException("服务器续传响应无效，拒绝拼接不完整文件")
            resetPart(target, task, part, offset)
            download(url, target, task, generation, part, allowRangeReset = false)
            return
        }
        persist()
    }

    private fun refreshExpiredStream(task: Task, video: File, audio: File, generation: Long) {
        checkActive(task, generation)
        if (task.bvid.isBlank() || task.cid <= 0 || task.quality <= 0) {
            throw IOException("视频直链已过期。此任务缺少可刷新的视频标识，请重新解析后重新下载")
        }
        val refreshed = try {
            BiliRepository().refreshStream(task.bvid, task.cid, task.quality, task.cookie)
        } catch (e: Exception) {
            throw IOException("视频直链已过期，重新获取失败：" + (e.message ?: "请重新解析视频"), e)
        }
        if (refreshed.quality != task.quality) {
            throw IOException("刷新后 B 站返回的清晰度发生变化，为避免拼接错误已停止任务")
        }
        if (refreshed.isDash != !task.audioUrl.isNullOrBlank()) {
            throw IOException("刷新后视频流格式发生变化，为避免损坏文件已停止任务")
        }
        task.videoUrl = refreshed.videoUrl
        task.audioUrl = refreshed.audioUrl
        if (task.videoEtag.isBlank() && task.videoModified.isBlank()) resetPart(video, task, "video", video.length())
        if (!task.audioUrl.isNullOrBlank() && task.audioEtag.isBlank() && task.audioModified.isBlank()) {
            resetPart(audio, task, "audio", audio.length())
        }
        task.downloaded = video.length() + if (task.audioUrl.isNullOrBlank()) 0L else audio.length()
        persist()
    }

    private fun resetPart(file: File, task: Task, part: String, oldLength: Long) {
        file.delete()
        task.downloaded = (task.downloaded - oldLength).coerceAtLeast(0L)
        if (part == "video") {
            task.videoEtag = ""
            task.videoModified = ""
            task.videoTotal = 0
        } else {
            task.audioEtag = ""
            task.audioModified = ""
            task.audioTotal = 0
        }
        task.total = task.videoTotal + task.audioTotal
        persist()
    }

    private fun setPartEtag(task: Task, part: String, value: String) {
        if (part == "video") task.videoEtag = value else task.audioEtag = value
    }

    private fun setPartModified(task: Task, part: String, value: String) {
        if (part == "video") task.videoModified = value else task.audioModified = value
    }

    private fun setPartTotal(task: Task, part: String, value: Long) {
        if (part == "video") task.videoTotal = value else task.audioTotal = value
    }

    private fun mux(video: File, audio: File, output: File) {
        val ve = MediaExtractor()
        val ae = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ve.setDataSource(video.absolutePath)
            ae.setDataSource(audio.absolutePath)
            val vi = findTrack(ve, "video/")
            val ai = findTrack(ae, "audio/")
            if (vi < 0) throw IOException("视频编码格式无法识别")
            if (ai < 0) throw IOException("音频编码格式无法识别")
            ve.selectTrack(vi)
            ae.selectTrack(ai)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val vt = muxer.addTrack(ve.getTrackFormat(vi))
            val at = muxer.addTrack(ae.getTrackFormat(ai))
            muxer.start()
            val readers = listOf(Reader(ve, vt), Reader(ae, at))
            while (true) {
                readers.forEach { it.load() }
                val next = readers.filter { it.size >= 0 }.minByOrNull { it.timeUs } ?: break
                val info = MediaCodec.BufferInfo().apply { set(0, next.size, next.timeUs, next.flags) }
                next.buffer.position(0)
                next.buffer.limit(next.size)
                muxer.writeSampleData(next.outputTrack, next.buffer, info)
                next.extractor.advance()
                next.clear()
            }
            muxer.stop()
        } catch (e: Exception) {
            throw IOException("音视频合并失败：" + (e.message ?: "设备可能不支持该编码格式"), e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            ve.release()
            ae.release()
        }
        if (!output.exists() || output.length() == 0L) throw IOException("合并后的视频文件为空")
    }

    private class Reader(val extractor: MediaExtractor, val outputTrack: Int) {
        val buffer: ByteBuffer = ByteBuffer.allocate(4 * 1024 * 1024)
        var size = -1
        var timeUs = Long.MAX_VALUE
        var flags = 0
        fun load() {
            if (size >= 0) return
            buffer.clear()
            val n = extractor.readSampleData(buffer, 0)
            if (n < 0) { size = -1; timeUs = Long.MAX_VALUE; return }
            size = n
            timeUs = extractor.sampleTime
            flags = extractor.sampleFlags
        }
        fun clear() { size = -1; timeUs = Long.MAX_VALUE }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix)) return i
        }
        return -1
    }

    private fun publish(file: File, title: String, bvid: String, quality: Int) {
        val settings = app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val folder = settings.getString("download_folder", "BilibiliDownload").orEmpty()
            .replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(48).ifBlank { "BilibiliDownload" }
        val template = settings.getString("filename_template", "{title}").orEmpty().ifBlank { "{title}" }
        val outputName = safeName(template.replace("{title}", title)
            .replace("{bvid}", bvid.ifBlank { "video" })
            .replace("{quality}", quality.toString()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, outputName + ".mp4")
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建系统下载文件")
            try {
                app.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("无法写入系统下载文件")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                app.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                app.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val base = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("设备没有可用的下载目录")
            val dir = File(base, folder)
            dir.mkdirs()
            file.copyTo(File(dir, outputName + ".mp4"), overwrite = true)
        }
    }

    @Synchronized
    private fun persist() {
        val rows = JSONArray()
        tasks.values.forEach { t ->
            rows.put(JSONObject().put("id", t.id).put("title", t.title)
                .put("videoUrl", t.videoUrl).put("audioUrl", t.audioUrl)
                .put("status", t.status).put("downloaded", t.downloaded)
                .put("total", t.total).put("error", t.error)
                .put("bvid", t.bvid).put("cid", t.cid).put("quality", t.quality)
                .put("videoEtag", t.videoEtag).put("audioEtag", t.audioEtag)
                .put("videoModified", t.videoModified).put("audioModified", t.audioModified)
                .put("videoTotal", t.videoTotal).put("audioTotal", t.audioTotal))
        }
        prefs.edit().putString("tasks_secure", encrypt(rows.toString())).remove("tasks").apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        if (packed.size <= 12) throw IOException("下载任务加密数据无效")
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val alias = "bilibili_download_task_key_v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    private fun restoreTasks() {
        val raw = try {
            prefs.getString("tasks_secure", null)?.let { decrypt(it) } ?: prefs.getString("tasks", "[]")
        } catch (_: Exception) { "[]" }
        val rows = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val migrateLegacy = prefs.contains("tasks")
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optLong("id")
            val video = row.optString("videoUrl")
            val audio = row.optString("audioUrl").takeIf { it.isNotBlank() && it != "null" }
            if (id == 0L || video.isBlank()) continue
            val task = Task(
                id = id, title = row.optString("title", "bilibili_video"),
                videoUrl = video, audioUrl = audio,
                status = row.optInt("status", 1), downloaded = row.optLong("downloaded"),
                total = row.optLong("total"), error = row.optString("error"),
                bvid = row.optString("bvid"), cid = row.optLong("cid"), quality = row.optInt("quality"),
                videoEtag = row.optString("videoEtag"), audioEtag = row.optString("audioEtag"),
                videoModified = row.optString("videoModified"), audioModified = row.optString("audioModified"),
                videoTotal = row.optLong("videoTotal"), audioTotal = row.optLong("audioTotal")
            )
            if (task.status == 2 || task.status == 3) task.status = 1
            tasks[id] = task
        }
        if (migrateLegacy) persist()
    }

    private fun checkActive(task: Task, generation: Long) {
        if (task.cancelled || Thread.currentThread().isInterrupted) throw IOException("任务已取消")
        if (task.paused || task.generation != generation) throw IOException("任务已暂停")
    }

    private fun workName(id: Long) = "dash_" + id.toString().removePrefix("-")
    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "bilibili_video" }
}
