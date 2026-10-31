package com.sxsbsxs.bilibilidownload.core.manager

import android.content.Context

/** Persists DownloadManager IDs as strings in SharedPreferences. */
class DownloadTaskRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("download_task_registry", Context.MODE_PRIVATE)

    private fun storedIds(): MutableSet<String> =
        prefs.getStringSet("ids", emptySet()).orEmpty().toMutableSet()

    fun all(): List<Long> = storedIds().mapNotNull(String::toLongOrNull)

    fun add(id: Long) {
        val ids = storedIds().apply { add(id.toString()) }
        prefs.edit().putStringSet("ids", ids).apply()
    }

    fun remove(id: Long) {
        val ids = storedIds().apply { remove(id.toString()) }
        prefs.edit().putStringSet("ids", ids).apply()
    }

    fun clear() {
        prefs.edit().remove("ids").apply()
    }
}
