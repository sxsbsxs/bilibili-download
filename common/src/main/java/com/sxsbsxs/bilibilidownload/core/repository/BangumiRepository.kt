package com.sxsbsxs.bilibilidownload.core.repository

import com.sxsbsxs.bilibilidownload.core.model.BangumiEpisode
import com.sxsbsxs.bilibilidownload.core.model.BangumiId
import com.sxsbsxs.bilibilidownload.core.model.BangumiInfo
import com.sxsbsxs.bilibilidownload.core.network.BiliHttpClient
import com.sxsbsxs.bilibilidownload.core.parser.BangumiIdParser
import org.json.JSONArray
import java.io.IOException

class BangumiRepository(private val http: BiliHttpClient = BiliHttpClient()) {
    fun parse(raw: String, cookie: String? = null): BangumiInfo {
        val id = BangumiIdParser.parse(raw) ?: throw IOException("无法识别 EP 或 SS 番剧链接")
        val query = when (id) {
            is BangumiId.Episode -> "ep_id=" + id.id
            is BangumiId.Season -> "season_id=" + id.id
        }
        val root = http.getJson("https://api.bilibili.com/pgc/view/web/season?$query", cookie)
        if (root.optInt("code", -1) != 0) throw IOException(root.optString("message").ifBlank { "番剧解析失败" })
        val data = root.optJSONObject("result") ?: root.optJSONObject("data") ?: throw IOException("B站没有返回番剧详情")
        val seasonId = data.optLong("season_id", if (id is BangumiId.Season) id.id else 0L)
        val title = data.optString("title").ifBlank { data.optString("season_title").ifBlank { "哔哩哔哩番剧" } }
        val cover = data.optString("cover")
        val rows = JSONArray()
        data.optJSONArray("episodes")?.let { appendUnique(rows, it) }
        data.optJSONArray("section")?.let { sections ->
            for (i in 0 until sections.length()) sections.optJSONObject(i)?.optJSONArray("episodes")?.let { appendUnique(rows, it) }
        }
        val episodes = buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val epId = row.optLong("id").takeIf { it > 0 } ?: row.optLong("ep_id")
                val aid = row.optLong("aid")
                val bvid = row.optString("bvid")
                val cid = row.optLong("cid")
                if (epId <= 0 || cid <= 0 || (bvid.isBlank() && aid <= 0)) continue
                val durationRaw = row.optLong("duration")
                add(BangumiEpisode(
                    epId, aid, bvid, cid,
                    row.optString("title").ifBlank { "第" + (size + 1) + "集" },
                    row.optString("long_title"),
                    if (durationRaw > 10000) (durationRaw / 1000L).toInt() else durationRaw.toInt(),
                    row.optString("cover").ifBlank { cover }, seasonId
                ))
            }
        }.distinctBy { it.epId }
        if (episodes.isEmpty()) throw IOException("番剧详情已返回，但没有可下载的剧集")
        return BangumiInfo(seasonId, title, cover, episodes, (id as? BangumiId.Episode)?.id)
    }

    private fun appendUnique(target: JSONArray, source: JSONArray) {
        val seen = (0 until target.length()).mapNotNull { target.optJSONObject(it)?.optLong("id") }.toMutableSet()
        for (i in 0 until source.length()) {
            val row = source.optJSONObject(i) ?: continue
            val id = row.optLong("id", row.optLong("ep_id"))
            if (id > 0 && seen.add(id)) target.put(row)
        }
    }
}
