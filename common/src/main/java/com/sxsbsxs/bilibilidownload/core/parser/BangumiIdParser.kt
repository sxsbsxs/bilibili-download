package com.sxsbsxs.bilibilidownload.core.parser

import com.sxsbsxs.bilibilidownload.core.model.BangumiId

object BangumiIdParser {
    private val episode = Regex("(?:^|[/\\s])ep(\\d+)(?:$|[?&#/\\s])", RegexOption.IGNORE_CASE)
    private val season = Regex("(?:^|[/\\s])ss(\\d+)(?:$|[?&#/\\s])", RegexOption.IGNORE_CASE)

    fun parse(raw: String): BangumiId? {
        val value = raw.trim()
        episode.find(value)?.groupValues?.get(1)?.toLongOrNull()?.let { return BangumiId.Episode(it) }
        season.find(value)?.groupValues?.get(1)?.toLongOrNull()?.let { return BangumiId.Season(it) }
        if (value.matches(Regex("ep\\d+", RegexOption.IGNORE_CASE))) return value.drop(2).toLongOrNull()?.let { BangumiId.Episode(it) }
        if (value.matches(Regex("ss\\d+", RegexOption.IGNORE_CASE))) return value.drop(2).toLongOrNull()?.let { BangumiId.Season(it) }
        return null
    }
}
