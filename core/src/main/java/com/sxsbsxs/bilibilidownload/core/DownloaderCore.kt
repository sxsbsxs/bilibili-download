package com.sxsbsxs.bilibilidownload.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class VideoInfo(val aid:Long,val bvid:String,val cid:Long,val title:String,val description:String,val duration:Int,val owner:String)
sealed interface VideoId { data class Bv(val value:String):VideoId; data class Av(val value:Long):VideoId }
object VideoIdParser {
 private val bv=Regex("BV[0-9A-Za-z]{10}",RegexOption.IGNORE_CASE)
 private val av=Regex("(?:^|[/\\s])av?([0-9]{1,15})(?:$|[?&#/\\s])",RegexOption.IGNORE_CASE)
 fun parse(raw:String):VideoId? { val s=raw.trim(); bv.find(s)?.value?.let{return VideoId.Bv(it)}; av.find(s)?.groupValues?.get(1)?.toLongOrNull()?.let{return VideoId.Av(it)}; if(s.matches(Regex("[0-9]{1,15}")))return s.toLongOrNull()?.let{VideoId.Av(it)}; return null }
}
class BiliApi {
 private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
 fun video(raw:String):VideoInfo {
  val id=VideoIdParser.parse(raw)?:throw IOException("无法识别 BV 号、AV 号或视频链接")
  val q=when(id){is VideoId.Bv->"bvid="+id.value;is VideoId.Av->"aid="+id.value}
  val root=json("https://api.bilibili.com/x/web-interface/view?"+q)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","B站接口错误"))
  val d=root.getJSONObject("data")
  return VideoInfo(d.optLong("aid"),d.optString("bvid"),d.optLong("cid"),d.optString("title"),d.optString("desc"),d.optInt("duration"),d.optJSONObject("owner")?.optString("name").orEmpty())
 }
 fun stream(v:VideoInfo,qn:Int=80):String {
  val root=json("https://api.bilibili.com/x/player/playurl?bvid="+v.bvid+"&cid="+v.cid+"&qn="+qn+"&fnval=0&fourk=1")
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","当前权限无法获取该清晰度"))
  val rows=root.optJSONObject("data")?.optJSONArray("durl")?:throw IOException("当前视频没有普通下载流")
  if(rows.length()==0)throw IOException("当前视频没有普通下载流")
  return rows.getJSONObject(0).optString("url").takeIf{it.isNotBlank()}?:throw IOException("下载地址为空")
 }
 private fun json(url:String):JSONObject {
  val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36").header("Referer","https://www.bilibili.com/").build()
  client.newCall(req).execute().use{r->if(!r.isSuccessful)throw IOException("HTTP "+r.code);return JSONObject(r.body?.string()?:throw IOException("B站返回空数据"))}
 }
}
class VideoDownloadManager(context:Context) {
 private val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 fun enqueue(url:String,title:String):Long {
  val name=title.replace(Regex("[\\\\/:*?\"<>|]"),"_").trim().take(90).ifBlank{"bilibili_video"}
  val req=DownloadManager.Request(Uri.parse(url)).setTitle(name).setDescription("Bilibili 视频下载").setMimeType("video/mp4").setAllowedOverMetered(true).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"BilibiliDownload/"+name+".mp4").addRequestHeader("User-Agent","Mozilla/5.0").addRequestHeader("Referer","https://www.bilibili.com/")
  return dm.enqueue(req)
 }
}
