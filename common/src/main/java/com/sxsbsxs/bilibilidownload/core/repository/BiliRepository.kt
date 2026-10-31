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
  val pages=buildList { val a=d.optJSONArray("pages");if(a!=null)for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;add(VideoPage(x.optLong("cid"),x.optInt("page",i+1),x.optString("part"),x.optInt("duration")))} }
  return VideoInfo(d.optLong("aid"),d.optString("bvid"),d.optLong("cid"),d.optString("title"),d.optString("desc"),d.optInt("duration"),d.optJSONObject("owner")?.optString("name").orEmpty(),d.optString("pic"),pages)
 }

 fun dashStreams(v:VideoInfo,page:VideoPage?=null,cookie:String?=null):Pair<List<StreamOption>,DashStreamInfo> {
  val cid=page?.cid?:v.cid
  val root=http.getJson("https://api.bilibili.com/x/player/playurl?bvid="+enc(v.bvid)+"&cid="+cid+"&qn=127&fnval=4048&fourk=1",cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","获取视频流失败"))
  val d=root.optJSONObject("data")?:throw IOException("视频流数据为空")
  val options=qualityOptions(d)
  val q=d.optInt("quality",0)
  val label=options.firstOrNull{it.quality==q}?.description?:("清晰度 "+q)
  val dash=d.optJSONObject("dash")
  val videos=dash?.optJSONArray("video")
  if(videos!=null&&videos.length()>0) {
   val vrow=(0 until videos.length()).mapNotNull{videos.optJSONObject(it)}.firstOrNull{it.optInt("id")==q}
    ?: (0 until videos.length()).mapNotNull{videos.optJSONObject(it)}.maxByOrNull{it.optInt("id")}
    ?:throw IOException("DASH 视频流列表为空")
   val audios=dash.optJSONArray("audio")
   val arow=audios?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)}.maxByOrNull{it.optLong("bandwidth")}}
   val vu=vrow.optString("baseUrl").ifBlank{vrow.optString("base_url")}
   val au=if(arow==null)null else arow.optString("baseUrl").ifBlank{arow.optString("base_url")}
   if(vu.isBlank()||au.isNullOrBlank())throw IOException("B站没有返回完整的 DASH 音视频流")
   return options to DashStreamInfo(vu,au,q,label,vrow.optString("codecs"),arow?.optString("codecs").orEmpty(),true)
  }
  val durl=d.optJSONArray("durl")
  if(durl!=null&&durl.length()>0)return options to DashStreamInfo(durl.getJSONObject(0).optString("url"),null,q,label,isDash=false)
  throw IOException("B站没有返回可用视频流")
 }

 fun dashStreamAtQuality(v:VideoInfo,q:Int,page:VideoPage?=null,cookie:String?=null):DashStreamInfo {
  val cid=page?.cid?:v.cid
  val root=http.getJson("https://api.bilibili.com/x/player/playurl?bvid="+enc(v.bvid)+"&cid="+cid+"&qn="+q+"&fnval=4048&fourk=1",cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","当前账号或游客权限无法获取该清晰度"))
  val d=root.optJSONObject("data")?:throw IOException("视频流数据为空")
  val actual=d.optInt("quality",q)
  val label=qualityOptions(d).firstOrNull{it.quality==actual}?.description?:("清晰度 "+actual)
  val dash=d.optJSONObject("dash")
  val videos=dash?.optJSONArray("video")
  if(videos!=null&&videos.length()>0) {
   val vrow=(0 until videos.length()).mapNotNull{videos.optJSONObject(it)}.firstOrNull{it.optInt("id")==actual}
    ?: (0 until videos.length()).mapNotNull{videos.optJSONObject(it)}.maxByOrNull{it.optInt("id")}
    ?:throw IOException("DASH 视频流列表为空")
   val audios=dash.optJSONArray("audio")
   val arow=audios?.let{a->(0 until a.length()).mapNotNull{a.optJSONObject(it)}.maxByOrNull{it.optLong("bandwidth")}}
   val vu=vrow.optString("baseUrl").ifBlank{vrow.optString("base_url")}
   val au=arow?.optString("baseUrl")?.ifBlank{arow.optString("base_url")}
   if(vu.isNotBlank()&&!au.isNullOrBlank())return DashStreamInfo(vu,au,actual,label,vrow.optString("codecs"),arow?.optString("codecs").orEmpty(),true)
  }
  val durl=d.optJSONArray("durl")
  if(durl!=null&&durl.length()>0)return DashStreamInfo(durl.getJSONObject(0).optString("url"),null,actual,label,isDash=false)
  throw IOException("所选清晰度没有可用视频流")
 }

 fun refreshStream(bvid:String,cid:Long,quality:Int,cookie:String?=null):DashStreamInfo {
  if(bvid.isBlank()||cid<=0||quality<=0)throw IOException("缺少刷新视频直链所需的 BV 号、CID 或清晰度")
  val video=VideoInfo(0,bvid,cid,"","",0,"")
  return dashStreamAtQuality(video,quality,null,cookie)
 }
 fun streams(v:VideoInfo,page:VideoPage?=null,cookie:String?=null):Pair<List<StreamOption>,StreamInfo> {
  val (opts,stream)=dashStreams(v,page,cookie)
  if(stream.isDash)throw IOException("该清晰度为 DASH 独立音视频流，请使用 DASH 下载")
  return opts to StreamInfo(stream.videoUrl,stream.quality,stream.description)
 }
 fun streamAtQuality(v:VideoInfo,q:Int,page:VideoPage?=null,cookie:String?=null):StreamInfo {
  val s=dashStreamAtQuality(v,q,page,cookie)
  if(s.isDash)throw IOException("该清晰度为 DASH 独立音视频流，请使用 DASH 下载")
  return StreamInfo(s.videoUrl,s.quality,s.description)
 }
 fun subtitles(v:VideoInfo,page:VideoPage?=null,cookie:String?=null):List<SubtitleTrack> {
  val cid=page?.cid?:v.cid
  val root=http.getJson("https://api.bilibili.com/x/player/v2?bvid="+enc(v.bvid)+"&cid="+cid,cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","获取字幕列表失败"))
  val rows=root.optJSONObject("data")?.optJSONObject("subtitle")?.optJSONArray("subtitles")?:return emptyList()
  return buildList { for(i in 0 until rows.length()){val x=rows.optJSONObject(i)?:continue;var url=x.optString("subtitle_url").ifBlank{x.optString("url")};if(url.startsWith("//"))url="https:"+url;if(url.startsWith("https://"))add(SubtitleTrack(x.optString("lan_doc").ifBlank{x.optString("lan").ifBlank{"字幕"}},url))} }
 }
 fun danmakuUrl(cid:Long)="https://comment.bilibili.com/"+cid+".xml"
 fun accountProfile(cookie:String):org.json.JSONObject {
  val root=http.getJson("https://api.bilibili.com/x/web-interface/nav",cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","读取账号信息失败"))
  return root.optJSONObject("data")?:throw IOException("账号信息为空")
 }
 fun fetchText(url:String,cookie:String?=null):String {
  if(!url.startsWith("https://"))throw IOException("只允许下载 HTTPS 资源")
  return http.getText(url,cookie)
 }
 private fun qualityOptions(d:org.json.JSONObject):List<StreamOption> {
  val ids=d.optJSONArray("accept_quality");val desc=d.optJSONArray("accept_description")
  return buildList { if(ids!=null)for(i in 0 until ids.length()){val id=ids.optInt(i);add(StreamOption(id,desc?.optString(i).orEmpty().ifBlank{"清晰度 "+id}))} }
 }
 private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
