@file:Suppress("unused")
package com.sxsbsxs.bilibilidownload.core

import android.content.Context
import com.sxsbsxs.bilibilidownload.core.download.AndroidDownloadService
import com.sxsbsxs.bilibilidownload.core.model.*
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository

typealias VideoInfo = com.sxsbsxs.bilibilidownload.core.model.VideoInfo
typealias VideoPage = com.sxsbsxs.bilibilidownload.core.model.VideoPage
typealias StreamOption = com.sxsbsxs.bilibilidownload.core.model.StreamOption
typealias VideoId = com.sxsbsxs.bilibilidownload.core.parser.VideoId
object VideoIdParser { fun parse(raw: String): VideoId? = com.sxsbsxs.bilibilidownload.core.parser.VideoIdParser.parse(raw) }
class BiliApi {
 private val repository=BiliRepository()
 fun video(raw:String):VideoInfo=repository.video(raw)
 fun stream(video:VideoInfo,qn:Int=80):String=repository.streamAtQuality(video,qn).url
 fun streams(video:VideoInfo):Pair<List<StreamOption>,StreamInfo> = repository.streams(video)
}
class VideoDownloadManager(context:Context) {
 private val service=AndroidDownloadService(context)
 fun enqueue(url:String,title:String):Long=service.enqueue(url,title)
 fun query(ids:List<Long>)=service.query(ids)
 fun remove(id:Long)=service.remove(id)
}