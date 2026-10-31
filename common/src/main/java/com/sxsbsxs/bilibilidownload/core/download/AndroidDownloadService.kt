package com.sxsbsxs.bilibilidownload.core.download
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
class AndroidDownloadService(context:Context) {
 private val manager=context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 fun enqueue(url:String,title:String,mimeType:String="video/mp4"):Long {
  val safe=title.replace(Regex("[\\\\/:*?\"<>|]"),"_").trim().take(90).ifBlank{"bilibili_video"}
  val request=DownloadManager.Request(Uri.parse(url)).setTitle(safe).setDescription("Bilibili 视频下载").setMimeType(mimeType)
   .setAllowedOverMetered(true).setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"BilibiliDownload/"+safe+".mp4")
   .addRequestHeader("User-Agent","Mozilla/5.0").addRequestHeader("Referer","https://www.bilibili.com/")
  return manager.enqueue(request)
 }
 fun query(ids:List<Long>):List<DownloadRecord> {
  if(ids.isEmpty())return emptyList()
  val cursor=manager.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?:return emptyList()
  return cursor.use{c->buildList{while(c.moveToNext())add(DownloadRecord(c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)).orEmpty(),c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))))}}
 }
 fun remove(id:Long){manager.remove(id)}
}