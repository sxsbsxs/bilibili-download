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
import com.sxsbsxs.bilibilidownload.core.model.DashStreamInfo
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * App-owned DASH task queue. Task metadata and partial files survive process death.
 * Cookies are deliberately not persisted; only signed stream URLs and task metadata are stored.
 */
class DashDownloadCoordinator(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("dash_download_tasks_v2", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().followRedirects(true).build()
    private val executor = Executors.newFixedThreadPool(2)
    private val sequence = AtomicLong()
    private val tasks = ConcurrentHashMap<Long, Task>()

    private data class Task(
        val id: Long,
        val title: String,
        val videoUrl: String,
        val audioUrl: String,
        @Volatile var status: Int = 1,
        @Volatile var downloaded: Long = 0,
        @Volatile var total: Long = 0,
        @Volatile var error: String = "",
        @Volatile var paused: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        @Volatile var cookie: String? = null,
        @Volatile var call: Call? = null
    )

    init {
        restoreTasks()
        tasks.values.filter { it.status == 1 || it.status == 2 }.forEach { task ->
            task.status = 1
            persist()
            schedule(task)
        }
    }

    @Synchronized
    fun enqueue(stream: DashStreamInfo, title: String, cookie: String?): Long {
        require(stream.isDash && !stream.audioUrl.isNullOrBlank()) { "DASH 音频流缺失" }
        val id = -(System.currentTimeMillis() * 1000L + sequence.incrementAndGet())
        val task = Task(id, safeName(title), stream.videoUrl, stream.audioUrl!!, cookie = cookie)
        tasks[id] = task
        persist()
        schedule(task)
        return id
    }

    fun query(): List<DownloadRecord> = tasks.values.sortedByDescending { it.id }.map {
        DownloadRecord(it.id, it.title, it.status, it.downloaded, it.total, it.error)
    }

    @Synchronized
    fun pause(id: Long): Boolean {
        val task = tasks[id] ?: return false
        if (task.status != 1 && task.status != 2) return false
        task.paused = true
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
        tasks.values.forEach { it.call?.cancel() }
        executor.shutdownNow()
    }

    private fun schedule(task: Task) {
        executor.execute { runTask(task) }
    }

    private fun runTask(task: Task) {
        val work = File(app.cacheDir, workName(task.id))
        try {
            work.mkdirs()
            val video = File(work, "video.m4s")
            val audio = File(work, "audio.m4s")
            download(task.videoUrl, video, task)
            checkActive(task)
            download(task.audioUrl, audio, task)
            checkActive(task)
            task.status = 3
            persist()
            val output = File(work, "merged.mp4")
            mux(video, audio, output)
            checkActive(task)
            publish(output, task.title)
            task.downloaded = output.length()
            task.total = output.length()
            task.status = 8
            task.error = ""
            persist()
        } catch (e: Exception) {
            when {
                task.cancelled -> Unit
                task.paused -> {
                    task.status = 4
                    task.error = ""
                }
                else -> {
                    task.status = 16
                    task.error = e.message ?: "DASH 下载失败"
                }
            }
            persist()
        } finally {
            task.call = null
            if (task.status == 8 || task.cancelled) work.deleteRecursively()
        }
    }

    private fun download(url: String, target: File, task: Task) {
        val offset = if (target.exists()) target.length() else 0L
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://www.bilibili.com/")
            .apply {
                if (offset > 0) header("Range", "bytes=$offset-")
                if (!task.cookie.isNullOrBlank()) header("Cookie", task.cookie!!)
            }.build()
        val call = client.newCall(request)
        task.call = call
        call.execute().use { response ->
            if (!response.isSuccessful && response.code != 206) throw IOException("下载媒体流失败：HTTP " + response.code)
            val body = response.body ?: throw IOException("B站返回空媒体流")
            val append = offset > 0 && response.code == 206
            if (offset > 0 && !append) {
                target.delete()
                task.downloaded = 0
            }
            val base = if (append) offset else 0L
            val length = body.contentLength().coerceAtLeast(0L)
            task.total = maxOf(task.total, base + length)
            body.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastPersisted = task.downloaded
                    while (true) {
                        checkActive(task)
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
        persist()
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

    private fun publish(file: File, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, title + ".mp4")
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BilibiliDownload")
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
            val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("设备没有可用的下载目录")
            dir.mkdirs()
            file.copyTo(File(dir, title + ".mp4"), overwrite = true)
        }
    }

    @Synchronized
    private fun persist() {
        val rows = JSONArray()
        tasks.values.forEach { t ->
            rows.put(JSONObject().put("id", t.id).put("title", t.title)
                .put("videoUrl", t.videoUrl).put("audioUrl", t.audioUrl)
                .put("status", t.status).put("downloaded", t.downloaded)
                .put("total", t.total).put("error", t.error))
        }
        prefs.edit().putString("tasks", rows.toString()).apply()
    }

    private fun restoreTasks() {
        val rows = try { JSONArray(prefs.getString("tasks", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optLong("id")
            val video = row.optString("videoUrl")
            val audio = row.optString("audioUrl")
            if (id == 0L || video.isBlank() || audio.isBlank()) continue
            val task = Task(id, row.optString("title", "bilibili_video"), video, audio,
                row.optInt("status", 1), row.optLong("downloaded"), row.optLong("total"),
                row.optString("error"))
            if (task.status == 2 || task.status == 3) task.status = 1
            tasks[id] = task
        }
    }

    private fun checkActive(task: Task) {
        if (task.cancelled || Thread.currentThread().isInterrupted) throw IOException("任务已取消")
        if (task.paused) throw IOException("任务已暂停")
    }

    private fun workName(id: Long) = "dash_" + id.toString().removePrefix("-")
    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "bilibili_video" }
}
