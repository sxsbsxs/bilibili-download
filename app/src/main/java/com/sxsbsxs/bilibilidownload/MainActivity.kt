package com.sxsbsxs.bilibilidownload

import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.Color
import android.webkit.WebSettings
import com.sxsbsxs.bilibilidownload.bridge.WebBridge

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var bridge: WebBridge

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(250, 250, 252)
        window.navigationBarColor = Color.rgb(250, 250, 252)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.rgb(250, 250, 252))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return !url.startsWith("file:///android_asset/web/")
                }
            }
            webChromeClient = WebChromeClient()
        }
        bridge = WebBridge(this, webView)
        webView.addJavascriptInterface(bridge, "Android")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("Android")
            webView.stopLoading()
            webView.destroy()
        }
        if (::bridge.isInitialized) bridge.close()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
