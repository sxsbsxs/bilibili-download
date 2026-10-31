package com.sxsbsxs.bilibilidownload.service.download

import android.content.Context
import com.sxsbsxs.bilibilidownload.core.download.AndroidDownloadService
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository

/** Coordinates Bilibili stream lookup with Android's persistent download queue. */
class DownloadCoordinator(context: Context) {
    private val repository = BiliRepository()
    private val downloadService = AndroidDownloadService(context)
    fun resolve(input: String) = repository.video(input)
    fun enqueue(url: String, title: String) = downloadService.enqueue(url, title)
    fun refresh(ids: List<Long>) = downloadService.query(ids)
    fun cancel(id: Long) = downloadService.remove(id)
}
