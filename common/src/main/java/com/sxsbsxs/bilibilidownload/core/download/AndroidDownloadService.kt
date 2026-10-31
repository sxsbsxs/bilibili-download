package com.sxsbsxs.bilibilidownload.core.download
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
import java.io.File
import java.io.IOException

class AndroidDownloadService(context:Context) {
 private val app=context.applicationContext
 private val manager=app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 fun enqueue(url:String,title:String,mimeType:String="video/mp4",extension:String="mp4"):Long {
  val safe=outputName(title)
  val folder=downloadFolder()
  val request=DownloadManager.Request(Uri.parse(url)).setTitle(safe).setDescription("Bilibili 视频下载").setMimeType(mimeType)
   .setAllowedOverMetered(true).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,folder+"/"+safe+"."+extension.trimStart('.').ifBlank{"mp4"})
   .addRequestHeader("User-Agent","Mozilla/5.0").addRequestHeader("Referer","https://www.bilibili.com/")
  return manager.enqueue(request)
 }
 /** Writes converted SRT/CSV locally; it does not route text through DownloadManager as a fake URL task. */
 fun saveText(content:String,title:String,mimeType:String,extension:String):String {
  val name=outputName(title)+"."+extension.trimStart('.')
  val folder=downloadFolder()
  if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
   val values=ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME,name)
    put(MediaStore.Downloads.MIME_TYPE,mimeType)
    put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/"+folder)
    put(MediaStore.Downloads.IS_PENDING,1)
   }
   val uri=app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    ?:throw IOException("无法创建导出文件")
   try {
    app.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use{it.write(content)}
     ?:throw IOException("无法写入导出文件")
    values.clear();values.put(MediaStore.Downloads.IS_PENDING,0)
    app.contentResolver.update(uri,values,null,null)
    return uri.toString()
   } catch(e:Exception) { app.contentResolver.delete(uri,null,null);throw e }
  }
  val dir=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?:throw IOException("设备没有可用的下载目录")
  val targetDir=File(dir,folder).apply{mkdirs()}
  val file=File(targetDir,name)
  file.writeText(content,Charsets.UTF_8)
  return file.absolutePath
 }
 fun query(ids:List<Long>):List<DownloadRecord> {
  if(ids.isEmpty())return emptyList()
  val cursor=manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?:return emptyList()
  return cursor.use{c->buildList{while(c.moveToNext())add(DownloadRecord(c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))))}}
 }
 fun remove(id:Long){manager.remove(id)}
 private fun downloadFolder():String=app.getSharedPreferences("app_settings",Context.MODE_PRIVATE)
  .getString("download_folder","BilibiliDownload").orEmpty().replace(Regex("[^A-Za-z0-9 _-]"),"").trim().take(48).ifBlank{"BilibiliDownload"}
 private fun outputName(value:String):String {
  val template=app.getSharedPreferences("app_settings",Context.MODE_PRIVATE).getString("filename_template","{title}").orEmpty()
  return safeName(template.replace("{title}",value).replace("{bvid}","").replace("{quality}",""))
 }
 private fun safeName(value:String)=value.replace(Regex("[\\\\/:*?\"<>|]"),"_").trim().take(90).ifBlank{"bilibili_export"}
}