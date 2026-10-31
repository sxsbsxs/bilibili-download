package com.sxsbsxs.bilibilidownload.core.parser

sealed interface VideoId {
    data class Bv(val value: String) : VideoId
    data class Av(val value: Long) : VideoId
}
object VideoIdParser {
    private val bv = Regex("BV[0-9A-Za-z]{10}", RegexOption.IGNORE_CASE)
    private val av = Regex("(?:^|[/\\s])av?([0-9]{1,15})(?:$|[?&#/\\s])", RegexOption.IGNORE_CASE)
    fun parse(raw: String): VideoId? {
        val value = raw.trim()
        bv.find(value)?.value?.let { return VideoId.Bv(it) }
        av.find(value)?.groupValues?.get(1)?.toLongOrNull()?.let { return VideoId.Av(it) }
        if (value.matches(Regex("[0-9]{1,15}"))) return value.toLongOrNull()?.let { VideoId.Av(it) }
        return null
    }
}
