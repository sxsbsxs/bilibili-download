package com.sxsbsxs.bilibilidownload.core.storage
import android.content.Context
class QualityPreferences(context:Context) {
 private val prefs=context.getSharedPreferences("download_preferences",Context.MODE_PRIVATE)
 var quality:Int get()=prefs.getInt("quality",80);set(v){prefs.edit().putInt("quality",v).apply()}
 var wifiOnly:Boolean get()=prefs.getBoolean("wifi_only",false);set(v){prefs.edit().putBoolean("wifi_only",v).apply()}
}