package com.sxsbsxs.bilibilidownload.library

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import org.json.JSONObject

/** Container-level processing using Android's extractor/muxer APIs (no transcoding). */
class MediaProcessingRepository(private val context: Context) {
    fun inspect(uriText: String): JSONObject {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriText))
            return JSONObject()
                .put("title", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty())
                .put("durationMs", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L)
                .put("width", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0)
                .put("height", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0)
                .put("bitrate", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L)
                .put("mime", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty())
                .put("rotation", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0)
        } finally {
            retriever.release()
        }
    }

    fun extractAac(uriText: String, sourceName: String): String {
        val extractor = MediaExtractor()
        val input = Uri.parse(uriText)
        val output = File(context.cacheDir, "audio_extract_${System.nanoTime()}.m4a")
        var muxer: MediaMuxer? = null
        try {
            context.contentResolver.openFileDescriptor(input, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("无法读取源媒体")
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() == "audio/mp4a-latm"
            } ?: throw IOException("未找到 AAC 音轨；当前版本仅支持无损提取 AAC，不会进行重新编码")
            extractor.selectTrack(track)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            copySelectedTracks(extractor, muxer, setOf(track), mapOf(track to outTrack))
            muxer.stop()
            return publish(output, safeName(sourceName) + "_audio.m4a", "audio/mp4")
        } catch (e: Exception) {
            throw IOException("提取 AAC 音频失败：" + (e.message ?: "不支持的媒体格式"), e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
            output.delete()
        }
    }

    fun remuxMp4(uriText: String, sourceName: String): String {
        val extractor = MediaExtractor()
        val output = File(context.cacheDir, "remux_${System.nanoTime()}.mp4")
        var muxer: MediaMuxer? = null
        try {
            context.contentResolver.openFileDescriptor(Uri.parse(uriText), "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: throw IOException("无法读取源媒体")
            val selected = (0 until extractor.trackCount).filter {
                val mime = extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
                mime.startsWith("video/") || mime.startsWith("audio/")
            }
            if (selected.isEmpty()) throw IOException("没有可封装的音视频轨道")
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTracks = selected.associateWith { muxer.addTrack(extractor.getTrackFormat(it)) }
            selected.forEach { extractor.selectTrack(it) }
            muxer.start()
            copySelectedTracks(extractor, muxer, selected.toSet(), outputTracks)
            muxer.stop()
            return publish(output, safeName(sourceName) + "_remux.mp4", "video/mp4")
        } catch (e: Exception) {
            throw IOException("MP4 重新封装失败：" + (e.message ?: "设备不支持该编码或容器"), e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
            output.delete()
        }
    }

    private fun copySelectedTracks(extractor: MediaExtractor, muxer: MediaMuxer, selected: Set<Int>, outputTracks: Map<Int, Int>) {
        val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val index = extractor.sampleTrackIndex
            if (index < 0) break
            if (index !in selected) {
                if (!extractor.advance()) break
                continue
            }
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
            muxer.writeSampleData(outputTracks[index] ?: throw IOException("音轨映射丢失"), buffer, info)
            if (!extractor.advance()) break
        }
    }

    private fun publish(source: File, name: String, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" +
                    (context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("download_folder", "BilibiliDownload") ?: "BilibiliDownload"))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建导出文件")
            try {
                context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("无法写入导出文件")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                return uri.toString()
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IOException("下载目录不可用")
        val folder = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).getString("download_folder", "BilibiliDownload") ?: "BilibiliDownload"
        val dir = File(base, folder)
        dir.mkdirs()
        val target = File(dir, name)
        source.copyTo(target, overwrite = true)
        return target.absolutePath
    }

    private fun safeName(value: String) = value.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(72).ifBlank { "bilibili_media" }
}
