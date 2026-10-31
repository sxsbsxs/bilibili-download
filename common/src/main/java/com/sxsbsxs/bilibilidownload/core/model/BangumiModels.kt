package com.sxsbsxs.bilibilidownload.core.model

data class BangumiEpisode(
    val epId: Long,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val longTitle: String,
    val duration: Int,
    val cover: String,
    val seasonId: Long
) {
    fun asVideoInfo(): VideoInfo = VideoInfo(
        aid = aid, bvid = bvid, cid = cid,
        title = listOf(title, longTitle).filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { "番剧第" + epId + "集" },
        description = longTitle, duration = duration, owner = "番剧", cover = cover
    )
}

data class BangumiInfo(
    val seasonId: Long,
    val title: String,
    val cover: String,
    val episodes: List<BangumiEpisode>,
    val selectedEpisodeId: Long? = null
)

sealed interface BangumiId {
    data class Episode(val id: Long) : BangumiId
    data class Season(val id: Long) : BangumiId
}
