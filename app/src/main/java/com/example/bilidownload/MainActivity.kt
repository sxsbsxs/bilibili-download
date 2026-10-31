package com.example.bilidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.bilidownload.common.network.BiliApi
import com.example.bilidownload.common.download.StreamDownloader
import com.example.bilidownload.common.storage.CookieVault
import com.example.bilidownload.feature.compose.activity.BiliDownloadApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vault = CookieVault(this)
        val api = BiliApi()
        val downloader = StreamDownloader(this)
        setContent {
            BiliDownloadApp(
                api = api,
                downloader = downloader,
                vault = vault
            )
        }
    }
}
