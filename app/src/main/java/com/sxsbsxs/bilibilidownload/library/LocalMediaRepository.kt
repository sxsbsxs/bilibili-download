package com.sxsbsxs.bilibilidownload.library

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

data class LocalResource(val id:String,val name:String,val mime:String,val size:Long,val modified:Long,val uri:Uri,val category:String)

/** Indexes files created in this app's BilibiliDownload download folder. */
class LocalMediaRepository(private val context:Context) {
    fun list():List<LocalResource> = if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) queryMediaStore() else queryLegacyFiles()

    private fun queryMediaStore():List<LocalResource> {
        val collection=MediaStore.Files.getContentUri("external")
        val projection=arrayOf(MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME,MediaStore.Files.FileColumns.MIME_TYPE,MediaStore.Files.FileColumns.SIZE,MediaStore.Files.FileColumns.DATE_MODIFIED)
        val selection=MediaStore.Files.FileColumns.RELATIVE_PATH+" LIKE ? AND "+MediaStore.Files.FileColumns.IS_PENDING+"=0"
        val folder=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE).getString("download_folder","BilibiliDownload") ?: "BilibiliDownload"
        val rows=mutableListOf<LocalResource>()
        context.contentResolver.query(collection,projection,selection,arrayOf("%"+folder+"%"),MediaStore.Files.FileColumns.DATE_MODIFIED+" DESC")?.use { cursor ->
            val idCol=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val dateCol=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while(cursor.moveToNext()) {
                val id=cursor.getLong(idCol);val name=cursor.getString(nameCol)?: "未命名文件"
                val mime=cursor.getString(mimeCol)?:"application/octet-stream"
                val uri=ContentUris.withAppendedId(collection,id)
                rows.add(LocalResource(id.toString(),name,mime,cursor.getLong(sizeCol),cursor.getLong(dateCol),uri,category(mime,name)))
            }
        }
        return rows
    }

    private fun queryLegacyFiles():List<LocalResource> {
        val base=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return emptyList()
        val folder=context.getSharedPreferences("app_settings",Context.MODE_PRIVATE).getString("download_folder","BilibiliDownload") ?: "BilibiliDownload"
        val roots=listOf(base,File(base,folder)).filter{it.exists()}
        return roots.flatMap { root -> root.walkTopDown().filter{it.isFile}.toList() }.distinctBy{it.absolutePath}.map { file ->
            val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())?:"application/octet-stream"
            val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
            LocalResource(file.absolutePath,file.name,mime,file.length(),file.lastModified()/1000,uri,category(mime,file.name))
        }.sortedByDescending{it.modified}.toList()
    }

    fun delete(uriText:String):Boolean {
        val uri=Uri.parse(uriText)
        return if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) {
            val resource=list().firstOrNull { it.uri.toString()==uriText } ?: return false
            File(resource.id).delete()
        } else context.contentResolver.delete(uri,null,null)>0
    }

    fun open(uriText:String,mime:String) {
        val uri=Uri.parse(uriText)
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime.ifBlank{"*/*"}).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent,"打开文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(uriText:String,mime:String) {
        val uri=Uri.parse(uriText)
        val intent=Intent(Intent.ACTION_SEND).setType(mime.ifBlank{"*/*"}).putExtra(Intent.EXTRA_STREAM,uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent,"导出 / 分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun category(mime:String,name:String):String {
        val ext=name.substringAfterLast('.',"").lowercase()
        return when {
            mime.startsWith("video/")->"视频"
            mime.startsWith("audio/")->"音频"
            ext in listOf("srt","ass","vtt")->"字幕"
            ext in listOf("csv","xml","json","txt")->"数据"
            else->"其他"
        }
    }
}
