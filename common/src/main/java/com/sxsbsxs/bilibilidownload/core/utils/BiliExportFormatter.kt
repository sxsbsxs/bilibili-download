package com.sxsbsxs.bilibilidownload.core.utils

import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Locale

/** Converts the official Bilibili subtitle JSON and danmaku XML payloads to portable text formats. */
object BiliExportFormatter {
    fun subtitleSrt(jsonText: String): String {
        val root = JSONObject(jsonText)
        val body = root.optJSONArray("body") ?: throw IllegalArgumentException("字幕数据中没有 body 字段")
        val out = StringBuilder()
        var index = 1
        for (i in 0 until body.length()) {
            val row = body.optJSONObject(i) ?: continue
            val start = row.optDouble("from", Double.NaN)
            val end = row.optDouble("to", Double.NaN)
            val text = row.optString("content").replace("\r", "").replace("\n", " ").trim()
            if (!start.isFinite() || !end.isFinite() || end <= start || text.isBlank()) continue
            out.append(index++).append('\n')
                .append(timestamp(start)).append(" --> ").append(timestamp(end)).append('\n')
                .append(text).append("\n\n")
        }
        if (index == 1) throw IllegalArgumentException("字幕文件没有可转换的字幕条目")
        return out.toString()
    }

    fun danmakuCsv(xmlText: String): String {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlText))
        val out = StringBuilder("\uFEFFtime_seconds,mode,font_size,color,created_at,pool,user_hash,row_id,content\r\n")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "d") {
                val p = parser.getAttributeValue(null, "p").orEmpty().split(',')
                val content = parser.nextText()
                val time = p.getOrNull(0)?.toDoubleOrNull()?.toString().orEmpty()
                val mode = p.getOrNull(1).orEmpty()
                val size = p.getOrNull(2).orEmpty()
                val color = p.getOrNull(3).orEmpty()
                val created = p.getOrNull(4).orEmpty()
                val pool = p.getOrNull(5).orEmpty()
                val hash = p.getOrNull(6).orEmpty()
                val rowId = p.getOrNull(7).orEmpty()
                out.append(listOf(time, mode, size, color, created, pool, hash, rowId, content)
                    .joinToString(",") { csv(it) }).append("\r\n")
            }
            event = parser.next()
        }
        if (out.lineSequence().count() <= 1) throw IllegalArgumentException("弹幕 XML 中没有可导出的弹幕")
        return out.toString()
    }

    private fun timestamp(seconds: Double): String {
        val millis = (seconds * 1000.0).toLong().coerceAtLeast(0)
        val h = millis / 3_600_000
        val m = (millis % 3_600_000) / 60_000
        val s = (millis % 60_000) / 1_000
        val ms = millis % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, ms)
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""
}
