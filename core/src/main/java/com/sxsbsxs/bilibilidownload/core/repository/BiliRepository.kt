package com.sxsbsxs.bilibilidownload.core.repository

import com.sxsbsxs.bilibilidownload.core.model.*
import com.sxsbsxs.bilibilidownload.core.network.BiliHttpClient
import com.sxsbsxs.bilibilidownload.core.parser.VideoId
import com.sxsbsxs.bilibilidownload.core.parser.VideoIdParser
import java.io.IOException
import java.net.URLEncoder

class BiliRepository(private val http: BiliHttpClient = BiliHttpClient()) {
    fun video(raw: String): VideoInfo {
        val id = VideoIdParser.parse(raw) ?: throw IOException("无法识别 BV 号、AV 号或视频链接")
        val query = when (id) {
            is VideoId.Bv -> "bvid=" + enc(id.value)
            is VideoId.Av -> "aid=" + id.value
        }
        val root = http.getJson("https://api.bilibili.com/x/web-interface/view?$query")
        if (root.optInt("code", -1) != 0) throw IOException(root.optString("message", "视频解析失败"))
        val data = root.getJSONObject("data")
        val pages = buildList {
            val array = data.optJSONArray("pages")
            if (array != null) for (i in 0 until array.length()) {
                val p = array.optJSONObject(i) ?: continue
                add(VideoPage(p.optLong("cid"), p.optInt("page", i + 1), p.optString("part"), p.optInt("duration")))
            }
        }
        return VideoInfo(
            data.optLong("aid"), data.optString("bvid"), data.optLong("cid"),
            data.optString("title"), data.optString("desc"),
            data.optInt("duration"), data.optJSONObject("owner")?.optString("name").orEmpty(),
            data.optString("pic"), pages
        )
    }

    fun streams(video: VideoInfo, page: VideoPage? = null, cookie: String? = null): Pair<List<StreamOption>, StreamInfo> {
        val cid = page?.cid ?: video.cid
        val url = "https://api.bilibili.com/x/player/playurl?bvid=${enc(video.bvid)}&cid=$cid&qn=127&fnval=0&fourk=1"
        val root = http.getJson(url, cookie)
        if (root.optInt("code", -1) != 0) throw IOException(root.optString("message", "获取视频流失败"))
        val data = root.optJSONObject("data") ?: throw IOException("视频流数据为空")
        val qualityIds = data.optJSONArray("accept_quality")
        val descriptions = data.optJSONArray("accept_description")
        val options = buildList {
            if (qualityIds != null) for (i in 0 until qualityIds.length()) {
                add(StreamOption(qualityIds.optInt(i), descriptions?.optString(i).orEmpty().ifBlank { "清晰度 ${qualityIds.optInt(i)}" }))
            }
        }
        val durl = data.optJSONArray("durl") ?: throw IOException("该视频返回的是分段/DASH 流，当前下载器暂不支持直接合并")
        if (durl.length() == 0) throw IOException("没有可用的视频下载地址")
        val first = durl.getJSONObject(0)
        val actualQuality = data.optInt("quality", 0)
        val selected = options.firstOrNull { it.quality == actualQuality }?.description ?: "当前可用清晰度"
        val stream = StreamInfo(first.optString("url").takeIf { it.isNotBlank() } ?: throw IOException("下载地址为空"), actualQuality, selected)
        return options to stream
    }

    fun streamAtQuality(video: VideoInfo, quality: Int, page: VideoPage? = null, cookie: String? = null): StreamInfo {
        val cid = page?.cid ?: video.cid
        val root = http.getJson("https://api.bilibili.com/x/player/playurl?bvid=${enc(video.bvid)}&cid=$cid&qn=$quality&fnval=0&fourk=1", cookie)
        if (root.optInt("code", -1) != 0) throw IOException(root.optString("message", "当前账号或游客权限无法获取该清晰度"))
        val data = root.optJSONObject("data") ?: throw IOException("视频流数据为空")
        val rows = data.optJSONArray("durl") ?: throw IOException("当前返回 DASH 流，尚未实现音视频合并")
        if (rows.length() == 0) throw IOException("没有可用下载流")
        val row = rows.getJSONObject(0)
        return StreamInfo(row.optString("url").takeIf { it.isNotBlank() } ?: throw IOException("下载地址为空"), data.optInt("quality", quality), "清晰度 ${data.optInt("quality", quality)}")
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
