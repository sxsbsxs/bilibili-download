package com.sxsbsxs.bilibilidownload.core.model
data class VideoInfo(val aid:Long,val bvid:String,val cid:Long,val title:String,val description:String,val duration:Int,val owner:String,val cover:String="",val pages:List<VideoPage> = emptyList())
data class VideoPage(val cid:Long,val page:Int,val title:String,val duration:Int)
data class StreamOption(val quality:Int,val description:String,val format:String="",val available:Boolean=true)
data class StreamInfo(val url:String,val quality:Int,val description:String,val mimeType:String="video/mp4")
data class DownloadRecord(val id:Long,val title:String,val status:Int,val downloadedBytes:Long,val totalBytes:Long)
data class SubtitleTrack(val language:String,val url:String,val format:String="json")
