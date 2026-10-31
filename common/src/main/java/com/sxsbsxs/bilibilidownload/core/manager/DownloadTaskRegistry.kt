package com.sxsbsxs.bilibilidownload.core.manager
import android.content.Context
/** Keeps DownloadManager IDs so the task list survives app restarts. */
class DownloadTaskRegistry(context:Context) {
 private val prefs=context.getSharedPreferences("download_task_registry",Context.MODE_PRIVATE)
 fun all():List<Long> = prefs.getStringSet("ids",emptySet()).orEmpty().mapNotNull{it.toLongOrNull()}
 fun add(id:Long){val ids=all().toMutableSet().apply{add(id.toString())};prefs.edit().putStringSet("ids",ids).apply()}
 fun remove(id:Long){val ids=all().toMutableSet().apply{remove(id.toString())};prefs.edit().putStringSet("ids",ids).apply()}
 fun clear(){prefs.edit().remove("ids").apply()}
}