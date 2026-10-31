package com.sxsbsxs.bilibilidownload.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object DownloadNotificationChannels {
    const val DOWNLOADS = "bilibili_downloads"
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(DOWNLOADS, "下载通知", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "下载进度和完成通知"
                }
            )
        }
    }
}
