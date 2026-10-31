package com.sxsbsxs.bilibilidownload.core.repository
import com.sxsbsxs.bilibilidownload.core.model.*
import com.sxsbsxs.bilibilidownload.core.network.BiliHttpClient
import com.sxsbsxs.bilibilidownload.core.parser.*
import java.io.IOException
import java.net.URLEncoder
class BiliRepository(private val http:BiliHttpClient=BiliHttpClient()) {
 fun video(raw:String):VideoInfo {
  val id=VideoIdParser.parse(raw)?:throw IOException("无法识别 BV 号、AV 号或视频链接")
  val q=when(id){is VideoId.Bv->"bvid="+enc(id.value);is VideoId.Av->"aid="+id.value}
  val root=http.getJson("https://api.bilibili.com/x/web-interface/view?"+q)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","视频解析失败"))
  val d=root.getJSONObject("data")
  val pages=buildList{val a=d.optJSONArray("pages");if(a!=null)for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;add(VideoPage(x.optLong("cid"),x.optInt("page",i+1),x.optString("part"),x.optInt("duration")))}}
  return VideoInfo(d.optLong("aid"),d.optString("bvid"),d.optLong("cid"),d.optString("title"),d.optString("desc"),d.optInt("duration"),d.optJSONObject("owner")?.optString("name").orEmpty(),d.optString("pic"),pages)
 }
 fun streams(v:VideoInfo,page:VideoPage?=null,cookie:String?=null):Pair<List<StreamOption>,StreamInfo>{
  val cid=page?.cid?:v.cid
  val root=http.getJson("https://api.bilibili.com/x/player/playurl?bvid="+enc(v.bvid)+"&cid="+cid+"&qn=127&fnval=0&fourk=1",cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","获取视频流失败"))
  val d=root.optJSONObject("data")?:throw IOException("视频流数据为空")
  val ids=d.optJSONArray("accept_quality");val desc=d.optJSONArray("accept_description")
  val options=buildList{if(ids!=null)for(i in 0 until ids.length())add(StreamOption(ids.optInt(i),desc?.optString(i).orEmpty().ifBlank{"清晰度 "+ids.optInt(i)}))}
  val rows=d.optJSONArray("durl")?:throw IOException("该视频返回 DASH 流，当前版本暂不支持音视频合并")
  if(rows.length()==0)throw IOException("没有可用下载地址")
  val q=d.optInt("quality",0);val label=options.firstOrNull{it.quality==q}?.description?:"当前可用清晰度"
  return options to StreamInfo(rows.getJSONObject(0).optString("url"),q,label)
 }
 fun streamAtQuality(v:VideoInfo,q:Int,page:VideoPage?=null,cookie:String?=null):StreamInfo{
  val cid=page?.cid?:v.cid
  val root=http.getJson("https://api.bilibili.com/x/player/playurl?bvid="+enc(v.bvid)+"&cid="+cid+"&qn="+q+"&fnval=0&fourk=1",cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","当前账号或游客权限无法获取该清晰度"))
  val d=root.optJSONObject("data")?:throw IOException("视频流数据为空")
  val rows=d.optJSONArray("durl")?:throw IOException("当前返回 DASH 流，尚未实现音视频合并")
  if(rows.length()==0)throw IOException("没有可用下载流")
  val actual=d.optInt("quality",q);return StreamInfo(rows.getJSONObject(0).optString("url"),actual,"清晰度 "+actual)
 }
 private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}