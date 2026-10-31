package com.sxsbsxs.bilibilidownload.core.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sxsbsxs.bilibilidownload.core.model.DashStreamInfo
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class DashDownloadCoordinator(context: Context) {
 private val app=context.applicationContext
 private val client=OkHttpClient.Builder().followRedirects(true).build()
 private val executor=Executors.newFixedThreadPool(2)
 private val sequence=AtomicLong()
 private val tasks=ConcurrentHashMap<Long,Task>()
 private data class Task(val id:Long,val title:String,@Volatile var status:Int=2,@Volatile var downloaded:Long=0,@Volatile var total:Long=0,@Volatile var error:String="",@Volatile var cancelled:Boolean=false)

 fun enqueue(stream:DashStreamInfo,title:String,cookie:String?):Long {
  require(stream.isDash&&!stream.audioUrl.isNullOrBlank()){"DASH 音频流缺失"}
  val id=-(System.currentTimeMillis()*1000L+sequence.incrementAndGet())
  val task=Task(id,safeName(title));tasks[id]=task
  executor.execute {
   val work=File(app.cacheDir,"dash_"+id.toString().removePrefix("-"))
   try {
    work.mkdirs()
    val video=File(work,"video.m4s");val audio=File(work,"audio.m4s")
    download(stream.videoUrl,video,cookie,task);checkCancelled(task)
    download(stream.audioUrl!!,audio,cookie,task);checkCancelled(task)
    val output=File(work,"merged.mp4");mux(video,audio,output);checkCancelled(task)
    publish(output,task.title);task.downloaded=output.length();task.total=output.length();task.status=8
   } catch(e:Exception){task.error=e.message?:"DASH 下载失败";task.status=16}
   finally{work.deleteRecursively()}
  }
  return id
 }
 fun query():List<DownloadRecord> = tasks.values.sortedByDescending{it.id}.map{DownloadRecord(it.id,it.title,it.status,it.downloaded,it.total,it.error)}
 fun remove(id:Long){tasks[id]?.cancelled=true;tasks.remove(id)}
 fun close(){tasks.values.forEach{it.cancelled=true};executor.shutdownNow();tasks.clear()}

 private fun download(url:String,target:File,cookie:String?,task:Task) {
  val req=Request.Builder().url(url).header("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36").header("Referer","https://www.bilibili.com/").apply{if(!cookie.isNullOrBlank())header("Cookie",cookie)}.build()
  client.newCall(req).execute().use{response->
   if(!response.isSuccessful)throw IOException("下载 DASH 流失败：HTTP "+response.code)
   val body=response.body?:throw IOException("B站返回空媒体流")
   task.total+=body.contentLength().coerceAtLeast(0L)
   body.byteStream().use{input->FileOutputStream(target).use{output->
    val buffer=ByteArray(65536)
    while(true){checkCancelled(task);val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);task.downloaded+=n}
    output.fd.sync()
   }}
  }
 }
 private fun mux(video:File,audio:File,output:File) {
  val ve=MediaExtractor();val ae=MediaExtractor();var muxer:MediaMuxer?=null
  try {
   ve.setDataSource(video.absolutePath);ae.setDataSource(audio.absolutePath)
   val vi=findTrack(ve,"video/");val ai=findTrack(ae,"audio/")
   if(vi<0)throw IOException("视频编码格式无法识别");if(ai<0)throw IOException("音频编码格式无法识别")
   ve.selectTrack(vi);ae.selectTrack(ai)
   muxer=MediaMuxer(output.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
   val vt=muxer.addTrack(ve.getTrackFormat(vi));val at=muxer.addTrack(ae.getTrackFormat(ai));muxer.start()
   val readers=listOf(Reader(ve,vt),Reader(ae,at))
   while(true){readers.forEach{it.load()};val next=readers.filter{it.size>=0}.minByOrNull{it.timeUs}?:break
    val info=MediaCodec.BufferInfo().apply{set(0,next.size,next.timeUs,next.flags)}
    next.buffer.position(0);next.buffer.limit(next.size);muxer.writeSampleData(next.outputTrack,next.buffer,info)
    next.extractor.advance();next.clear()
   }
   muxer.stop()
  }catch(e:Exception){throw IOException("音视频合并失败："+(e.message?:"设备可能不支持该编码格式"),e)}
  finally{try{muxer?.release()}catch(_:Exception){};ve.release();ae.release()}
  if(!output.exists()||output.length()==0L)throw IOException("合并后的视频文件为空")
 }
 private class Reader(val extractor:MediaExtractor,val outputTrack:Int) {
  val buffer:ByteBuffer=ByteBuffer.allocate(4*1024*1024);var size=-1;var timeUs=Long.MAX_VALUE;var flags=0
  fun load(){if(size>=0)return;buffer.clear();val n=extractor.readSampleData(buffer,0);if(n<0){size=-1;timeUs=Long.MAX_VALUE;return};size=n;timeUs=extractor.sampleTime;flags=extractor.sampleFlags}
  fun clear(){size=-1;timeUs=Long.MAX_VALUE}
 }
 private fun findTrack(extractor:MediaExtractor,prefix:String):Int {for(i in 0 until extractor.trackCount)if(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty().startsWith(prefix))return i;return -1}
 private fun publish(file:File,title:String) {
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
   val values=ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,title+".mp4");put(MediaStore.Downloads.MIME_TYPE,"video/mp4");put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/BilibiliDownload");put(MediaStore.Downloads.IS_PENDING,1)}
   val uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)?:throw IOException("无法创建系统下载文件")
   try{app.contentResolver.openOutputStream(uri)?.use{out->file.inputStream().use{it.copyTo(out)}}?:throw IOException("无法写入系统下载文件");values.clear();values.put(MediaStore.Downloads.IS_PENDING,0);app.contentResolver.update(uri,values,null,null)}
   catch(e:Exception){app.contentResolver.delete(uri,null,null);throw e}
  }else{val dir=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?:throw IOException("设备没有可用的下载目录");dir.mkdirs();file.copyTo(File(dir,title+".mp4"),overwrite=true)}
 }
 private fun safeName(value:String)=value.replace(Regex("[\\\\/:*?\"<>|]"),"_").trim().take(80).ifBlank{"bilibili_video"}
 private fun checkCancelled(task:Task){if(task.cancelled||Thread.currentThread().isInterrupted)throw IOException("下载任务已取消")}
}
