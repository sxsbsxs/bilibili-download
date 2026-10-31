package com.sxsbsxs.bilibilidownload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.sxsbsxs.bilibilidownload.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity() {
 private val api=BiliApi()
 private lateinit var downloads:VideoDownloadManager
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); downloads=VideoDownloadManager(this)
  setContent {
   var input by remember{mutableStateOf("")}; var loading by remember{mutableStateOf(false)}
   var error by remember{mutableStateOf<String?>(null)}; var video by remember{mutableStateOf<VideoInfo?>(null)}
   var notice by remember{mutableStateOf<String?>(null)}
   MaterialTheme(colorScheme=lightColorScheme()) {
    Scaffold(topBar={Surface(shadowElevation=2.dp){Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
     Text("B",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.width(10.dp));Text("Bilibili Download",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Spacer(Modifier.weight(1f));Text("游客模式",style=MaterialTheme.typography.labelMedium)
    }}}){pad->
     Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
      Text("下载视频",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
      Text("粘贴 B 站视频链接，或输入 BV / AV 号",color=MaterialTheme.colorScheme.onSurfaceVariant)
      OutlinedTextField(input,{input=it;error=null},Modifier.fillMaxWidth(),placeholder={Text("BV 号 / AV 号 / 视频链接")},singleLine=true,shape=RoundedCornerShape(14.dp))
      Button(onClick={if(!loading){loading=true;error=null;video=null;lifecycleScope.launch{try{video=withContext(Dispatchers.IO){api.video(input)}}catch(e:Exception){error=e.message?:"解析失败"}finally{loading=false}}}},enabled=input.isNotBlank()&&!loading,modifier=Modifier.fillMaxWidth().height(50.dp)){if(loading)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Text("解析视频")}
      if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
      if(video!=null)ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
       Text(video!!.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("UP："+video!!.owner);Text("BV："+video!!.bvid);Text("时长："+(video!!.duration/60)+" 分 "+(video!!.duration%60)+" 秒")
       if(video!!.description.isNotBlank())Text(video!!.description,maxLines=4,color=MaterialTheme.colorScheme.onSurfaceVariant)
       Button(onClick={val v=video!!;lifecycleScope.launch{try{val u=withContext(Dispatchers.IO){api.stream(v)};notice="已加入系统下载队列：任务 "+downloads.enqueue(u,v.title)}catch(e:Exception){notice=e.message?:"下载任务创建失败"}}},modifier=Modifier.fillMaxWidth()){Text("下载当前可用视频流")}
      }}
      if(notice!=null)Text(notice!!,color=MaterialTheme.colorScheme.primary)
      Text("清晰度由 B 站对当前游客或账号开放的权限决定。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
 }
}
