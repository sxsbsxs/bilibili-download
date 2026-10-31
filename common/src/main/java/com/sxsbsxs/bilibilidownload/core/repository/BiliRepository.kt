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
  return VideoInfo(d.optLong("aid"),d.optString("bvid"),d.optLong("cid"),d.optString("title"),d.optString("desc"),d.optInt("duration"),d.optJSONObject("owner")?.optString("name").orEmpty(),normalizeImageUrl(d.optString("pic")),pages)
 }

 fun dashStreams(v:VideoInfo,page:VideoPage?=null,cookie:String?=null):Pair<List<StreamOption>,DashStreamInfo> {
  val cid=page?.cid?:v.cid
  val root=signedGet("https://api.bilibili.com/x/player/wbi/playurl", mapOf(
   "bvid" to v.bvid, "cid" to cid.toString(), "qn" to "127", "fnval" to "4048",
   "fnver" to "0", "fourk" to "1", "platform" to "pc"
  ),cookie)
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
  val root=signedGet("https://api.bilibili.com/x/player/wbi/playurl", mapOf(
   "bvid" to v.bvid, "cid" to cid.toString(), "qn" to q.toString(), "fnval" to "4048",
   "fnver" to "0", "fourk" to "1", "platform" to "pc"
  ),cookie)
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

 fun searchContent(keyword:String,kind:String,page:Int=1,cookie:String?=null):org.json.JSONObject {
  val q=keyword.trim()
  if(q.isBlank())throw IOException("请输入搜索关键词")
  val type=when(kind){"video"->"video";"bangumi"->"media_bangumi";"up"->"bili_user";else->throw IOException("不支持的搜索类型")}
  val safePage=page.coerceIn(1,50).toString()
  val params=mapOf(
   "search_type" to type, "keyword" to q, "page" to safePage,
   "page_size" to "20", "order" to "totalrank", "duration" to "0",
   "tids" to "0", "web_location" to "1550101"
  )
  // First use the current WBI endpoint with a guest session. If it rejects the request,
  // try the public legacy search route before reporting failure.
  val primary=try { signedGet("https://api.bilibili.com/x/web-interface/wbi/search/type",params,cookie) }
   catch (_:Exception) { null }
  if(primary!=null && primary.optInt("code",-1)==0)
   return primary.optJSONObject("data")?:throw IOException("搜索结果为空")
  val legacyUrl="https://api.bilibili.com/x/web-interface/search/type?search_type="+enc(type)+
   "&keyword="+enc(q)+"&page="+safePage+"&order=totalrank"
  val fallback=try { http.getJson(legacyUrl,cookie) } catch (e:Exception) {
   throw IOException(primary?.optString("message")?.takeIf{it.isNotBlank()}?:e.message?:"搜索失败")
  }
  if(fallback.optInt("code",-1)!=0) {
   val reason=fallback.optString("message").ifBlank{primary?.optString("message").orEmpty()}
   throw IOException(reason.ifBlank{"搜索失败，请稍后重试"})
  }
  return fallback.optJSONObject("data")?:throw IOException("搜索结果为空")
 }
 fun accountSection(section:String,page:Int=1,cookie:String):org.json.JSONObject {
  val profile=accountProfile(cookie)
  val mid=profile.optLong("mid")
  val p=page.coerceIn(1,100)
  val url=when(section){
   "favorites"->"https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid="+mid+"&jsonp=jsonp"
   "history"->"https://api.bilibili.com/x/web-interface/history/cursor?ps=20&max=0&view_at=0&business="
   "watchlater"->"https://api.bilibili.com/x/v2/history/toview"
   "likes"->"https://api.bilibili.com/x/space/like/arc?vmid="+mid+"&pn="+p+"&ps=20"
   "uploads"->return spaceArchiveSearch(mid,p,cookie)
   else->throw IOException("未知账号内容分类")
  }
  val root=http.getJson(url,cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","读取账号内容失败"))
  return root.optJSONObject("data")?:throw IOException("账号内容为空")
 }

 fun upVideos(mid:Long,page:Int=1,cookie:String?=null):org.json.JSONObject {
  if(mid<=0)throw IOException("UP 主 UID 无效")
  return spaceArchiveSearch(mid,page,cookie)
 }

 fun favoriteResources(mediaId:Long,page:Int=1,cookie:String):org.json.JSONObject {
  if(mediaId<=0)throw IOException("收藏夹 ID 无效")
  val url="https://api.bilibili.com/x/v3/fav/resource/list?media_id="+mediaId+"&pn="+page.coerceIn(1,100)+"&ps=20"
  val root=http.getJson(url,cookie)
  if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","读取收藏夹失败"))
  return root.optJSONObject("data")?:throw IOException("收藏夹内容为空")
 }

 fun fetchText(url:String,cookie:String?=null):String {
  if(!url.startsWith("https://"))throw IOException("只允许下载 HTTPS 资源")
  return http.getText(url,cookie)
 }

 private fun spaceArchiveSearch(mid:Long,page:Int,cookie:String?):org.json.JSONObject {
  return signedGet("https://api.bilibili.com/x/space/wbi/arc/search", mapOf(
   "mid" to mid.toString(), "order" to "pubdate", "pn" to page.coerceIn(1,100).toString(),
   "ps" to "20", "web_location" to "1550101"
  ),cookie).let { root ->
   if(root.optInt("code",-1)!=0)throw IOException(root.optString("message","读取 UP 主投稿失败"))
   root.optJSONObject("data")?:throw IOException("UP 主投稿为空")
  }
 }

 private fun signedGet(endpoint:String,parameters:Map<String,String>,cookie:String?,retryCookieBlock:Boolean=true):org.json.JSONObject {
  http.ensureWebCookies(cookie)
  val nav=http.getJson("https://api.bilibili.com/x/web-interface/nav",cookie)
  if(nav.optInt("code",-1)!=0)throw IOException(nav.optString("message","读取 WBI 签名参数失败"))
  val wbi=nav.optJSONObject("data")?.optJSONObject("wbi_img")?:throw IOException("B站没有返回 WBI 签名参数")
  val imgKey=wbi.optString("img_url").substringAfterLast('/').substringBefore('.')
  val subKey=wbi.optString("sub_url").substringAfterLast('/').substringBefore('.')
  if(imgKey.length<32||subKey.length<32)throw IOException("WBI 签名参数不完整")
  val raw=imgKey+subKey
  val mixin=intArrayOf(46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52)
  val mixinKey=mixin.map{raw[it]}.joinToString("").take(32)
  val sorted=java.util.TreeMap<String,String>()
  sorted.putAll(parameters)
  sorted["wts"]=(System.currentTimeMillis()/1000).toString()
  val query=sorted.entries.joinToString("&"){entry->
   entry.key+"="+URLEncoder.encode(entry.value.replace(Regex("[!'()*]"),""),"UTF-8")
  }
  val response=http.getJson(endpoint+"?"+query+"&w_rid="+md5(query+mixinKey),cookie)
  if(response.optInt("code",-1)==-412 && retryCookieBlock) {
   http.ensureWebCookies(cookie,force=true)
   return signedGet(endpoint,parameters,cookie,retryCookieBlock=false)
  }
  return response
 }

 private fun normalizeImageUrl(value:String):String = when {
  value.startsWith("//")->"https:"+value
  value.startsWith("http://")->"https://"+value.removePrefix("http://")
  else->value
 }

 private fun md5(value:String):String = java.security.MessageDigest.getInstance("MD5")
  .digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 0xff)}

 private fun qualityOptions(d:org.json.JSONObject):List<StreamOption> {
  val ids=d.optJSONArray("accept_quality");val desc=d.optJSONArray("accept_description")
  return buildList { if(ids!=null)for(i in 0 until ids.length()){val id=ids.optInt(i);add(StreamOption(id,desc?.optString(i).orEmpty().ifBlank{"清晰度 "+id}))} }
 }
 private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}
