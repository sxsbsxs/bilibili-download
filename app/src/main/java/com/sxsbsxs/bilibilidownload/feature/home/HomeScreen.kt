package com.sxsbsxs.bilibilidownload.feature.home

import android.content.Context
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
import com.sxsbsxs.bilibilidownload.core.BiliApi
import com.sxsbsxs.bilibilidownload.core.VideoDownloadManager
import com.sxsbsxs.bilibilidownload.core.VideoInfo
import com.sxsbsxs.bilibilidownload.core.model.StreamOption
import com.sxsbsxs.bilibilidownload.core.model.VideoPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(context: Context) {
    val api = remember { BiliApi() }
    val downloads = remember { VideoDownloadManager(context) }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var video by remember { mutableStateOf<VideoInfo?>(null) }
    var pages by remember { mutableStateOf<List<VideoPage>>(emptyList()) }
    var selectedPage by remember { mutableStateOf<VideoPage?>(null) }
    var qualities by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var selectedQuality by remember { mutableIntStateOf(80) }
    var notice by remember { mutableStateOf<String?>(null) }
    var taskIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var taskText by remember { mutableStateOf("暂无下载任务") }

    MaterialTheme(colorScheme = lightColorScheme()) {
        Scaffold(topBar = {
            Surface(shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("B", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Text("Bilibili Download", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("游客模式", style = MaterialTheme.typography.labelMedium)
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("视频解析", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("支持 BV 号、AV 号和 B 站视频链接")
                OutlinedTextField(input, { input = it; error = null }, Modifier.fillMaxWidth(), placeholder = { Text("粘贴 BV / AV / 视频链接") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Button(onClick = {
                    loading = true; error = null; video = null; qualities = emptyList()
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { api.video(input) }
                            video = result; pages = result.pages; selectedPage = result.pages.firstOrNull()
                            val streamData = withContext(Dispatchers.IO) { api.streams(result) }
                            qualities = streamData.first
                            selectedQuality = streamData.second.quality.takeIf { q -> qualities.any { it.quality == q } } ?: qualities.firstOrNull()?.quality ?: 80
                        } catch (e: Exception) { error = e.message ?: "解析失败" }
                        finally { loading = false }
                    }
                }, enabled = input.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("解析视频与清晰度")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                video?.let { v ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("UP 主：${v.owner}")
                            Text("BV：${v.bvid}  ·  AV：av${v.aid}")
                            Text("时长：${v.duration / 60} 分 ${v.duration % 60} 秒")
                            if (v.description.isNotBlank()) Text(v.description, maxLines = 4, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (pages.size > 1) {
                                Text("分 P 选择", fontWeight = FontWeight.SemiBold)
                                pages.forEach { p ->
                                    FilterChip(selected = selectedPage?.cid == p.cid, onClick = { selectedPage = p }, label = { Text("P${p.page} · ${p.title}") })
                                }
                            }
                            Text("可用清晰度", fontWeight = FontWeight.SemiBold)
                            if (qualities.isEmpty()) Text("清晰度信息暂不可用")
                            FlowRowCompat(qualities) { option ->
                                FilterChip(selected = selectedQuality == option.quality, onClick = { selectedQuality = option.quality }, label = { Text(option.description) })
                            }
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        val stream = withContext(Dispatchers.IO) { api.streams(v).second }
                                        val id = downloads.enqueue(stream.url, v.title)
                                        taskIds = taskIds + id
                                        notice = "已加入系统下载队列（任务 #$id）"
                                    } catch (e: Exception) { notice = e.message ?: "下载任务创建失败" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("下载当前可用流") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        val stream = withContext(Dispatchers.IO) { com.sxsbsxs.bilibilidownload.core.repository.BiliRepository().streamAtQuality(v, selectedQuality, selectedPage) }
                                        val id = downloads.enqueue(stream.url, v.title)
                                        taskIds = taskIds + id
                                        notice = "已加入下载队列：${stream.description}"
                                    } catch (e: Exception) { notice = e.message ?: "下载失败" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("按所选清晰度下载") }
                        }
                    }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("下载任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(taskText)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val records = withContext(Dispatchers.IO) { downloads.query(taskIds) }
                                    taskText = if (records.isEmpty()) "暂无下载任务" else records.joinToString("\n") { r ->
                                        val status = when (r.status) { 8 -> "已完成"; 16 -> "失败"; 2 -> "正在下载"; 4 -> "暂停"; else -> "排队/处理中" }
                                        "${r.title}：$status" + if (r.totalBytes > 0) "（${r.downloadedBytes / 1024} / ${r.totalBytes / 1024} KB）" else ""
                                    }
                                }
                            }) { Text("刷新状态") }
                            OutlinedButton(onClick = { taskIds.forEach(downloads::remove); taskIds = emptyList(); taskText = "任务已移除" }) { Text("清除任务") }
                        }
                    }
                }
                Text("说明：清晰度由 B 站当前账号/游客权限决定。DASH 音视频分离流暂未合并；不会绕过会员、地区或 DRM 限制。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FlowRowCompat(items: List<StreamOption>, content: @Composable (StreamOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach(content) } }
    }
}
