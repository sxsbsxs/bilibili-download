package com.sxsbsxs.bilibilidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sxsbsxs.bilibilidownload.feature.home.HomeScreen
import com.sxsbsxs.bilibilidownload.notification.DownloadNotificationChannels

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadNotificationChannels.create(this)
        setContent { HomeScreen(this) }
    }
}
