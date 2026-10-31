package com.sxsbsxs.bilibilidownload.feature.home

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sxsbsxs.bilibilidownload.core.BiliApi
import com.sxsbsxs.bilibilidownload.core.VideoDownloadManager
import com.sxsbsxs.bilibilidownload.core.VideoInfo
import com.sxsbsxs.bilibilidownload.core.model.DownloadRecord
import com.sxsbsxs.bilibilidownload.core.model.StreamOption
import com.sxsbsxs.bilibilidownload.core.model.VideoPage
import com.sxsbsxs.bilibilidownload.core.repository.BiliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MainTab(val title: String, val symbol: String) {
    HOME("首页", "⌂"), DOWNLOADS("下载", "↓"), RESOURCES("资源", "▤"), PROFILE("我的", "●")
}

@Composable
fun HomeScreen(context: Context) {
    val api = remember { BiliApi() }
    val downloads = remember { VideoDownloadManager(context) }
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
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
    var records by remember { mutableStateOf<List<DownloadRecord>>(emptyList()) }
    var refreshingTasks by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF00A1D6), onPrimary = Color.White,
        secondary = Color(0xFF52616B), background = Color(0xFFF5F7F9), surface = Color.White
    )) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = Color.White, shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFF00A1D6), shape = RoundedCornerShape(10.dp)) {
                            Text("B", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("Bilibili Download", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("视频资源下载器", color = Color(0xFF7B8790), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(color = Color(0xFFEAF7FC), shape = CircleShape) {
                            Text("游客模式", Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color(0xFF008DBD), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Text(tab.symbol, style = MaterialTheme.typography.titleLarge) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { padding ->
            when (currentTab) {
                MainTab.HOME -> HomeContent(
                    Modifier.fillMaxSize().padding(padding), input, { input = it; error = null },
                    loading, error, video, pages, selectedPage, { selectedPage = it },
                    qualities, selectedQuality, { selectedQuality = it }, notice,
                    onParse = {
                        loading = true; error = null; video = null; qualities = emptyList()
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) { api.video(input.trim()) }
                                video = result; pages = result.pages; selectedPage = result.pages.firstOrNull()
                                val streamData = withContext(Dispatchers.IO) { api.streams(result) }
                                qualities = streamData.first
                                selectedQuality = streamData.second.quality.takeIf { q -> qualities.any { it.quality == q } }
                                    ?: qualities.firstOrNull()?.quality ?: 80
                            } catch (e: Exception) {
                                error = e.message ?: "解析失败，请检查链接后重试"
                            } finally { loading = false }
                        }
                    },
                    onDownload = {
                        val current = video
                        if (current != null) scope.launch {
                            try {
                                val stream = withContext(Dispatchers.IO) {
                                    BiliRepository().streamAtQuality(current, selectedQuality, selectedPage)
                                }
                                val id = withContext(Dispatchers.IO) { downloads.enqueue(stream.url, current.title) }
                                taskIds = taskIds + id
                                notice = "已加入下载队列 · 任务 #$id"
                                currentTab = MainTab.DOWNLOADS
                            } catch (e: Exception) { notice = e.message ?: "创建下载任务失败" }
                        }
                    }
                )
                MainTab.DOWNLOADS -> DownloadsContent(
                    Modifier.fillMaxSize().padding(padding), records, refreshingTasks,
                    onRefresh = {
                        refreshingTasks = true
                        scope.launch {
                            records = withContext(Dispatchers.IO) { downloads.query(taskIds) }
                            refreshingTasks = false
                        }
                    },
                    onRemove = { id ->
                        downloads.remove(id); taskIds = taskIds - id; records = records.filterNot { it.id == id }
                    }
                )
                MainTab.RESOURCES -> ResourcesContent(Modifier.fillMaxSize().padding(padding), records.size)
                MainTab.PROFILE -> ProfileContent(Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier, input: String, onInputChange: (String) -> Unit, loading: Boolean, error: String?,
    video: VideoInfo?, pages: List<VideoPage>, selectedPage: VideoPage?, onPageSelected: (VideoPage) -> Unit,
    qualities: List<StreamOption>, selectedQuality: Int, onQualitySelected: (Int) -> Unit, notice: String?,
    onParse: () -> Unit, onDownload: () -> Unit
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("下载 B 站视频", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("粘贴视频链接，或输入 BV / AV 号开始解析", color = Color(0xFF78848D), style = MaterialTheme.typography.bodyMedium)
        }
        ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("视频地址", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(input, onInputChange, Modifier.fillMaxWidth(), placeholder = { Text("https://www.bilibili.com/video/BV...") }, singleLine = true, shape = RoundedCornerShape(13.dp))
                Button(onClick = onParse, enabled = input.isNotBlank() && !loading, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp)) {
                    if (loading) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp) else Text("解析视频")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickInfoCard("支持格式", "BV · AV · URL", Modifier.weight(1f))
            QuickInfoCard("下载方式", "系统下载服务", Modifier.weight(1f))
        }
        video?.let { v ->
            ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Text("视频信息", color = Color(0xFF008DBD), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(v.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("UP 主：${v.owner}", color = Color(0xFF697780), style = MaterialTheme.typography.bodySmall)
                    Text("BV：${v.bvid}    AV：av${v.aid}", color = Color(0xFF697780), style = MaterialTheme.typography.bodySmall)
                    Text("时长：${v.duration / 60} 分 ${v.duration % 60} 秒", color = Color(0xFF697780), style = MaterialTheme.typography.bodySmall)
                    if (pages.size > 1) {
                        HorizontalDivider(color = Color(0xFFE9EEF1))
                        Text("选择分 P", fontWeight = FontWeight.SemiBold)
                        pages.forEach { page ->
                            FilterChip(selectedPage?.cid == page.cid, { onPageSelected(page) }, label = { Text("P${page.page} · ${page.title}", maxLines = 1) })
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE9EEF1))
                    Text("清晰度", fontWeight = FontWeight.SemiBold)
                    if (qualities.isEmpty()) Text("暂未获取到可选清晰度", color = Color(0xFF78848D), style = MaterialTheme.typography.bodySmall)
                    else qualities.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { option ->
                                FilterChip(selectedQuality == option.quality, { onQualitySelected(option.quality) }, label = { Text(option.description) })
                            }
                        }
                    }
                    Button(onClick = onDownload, enabled = qualities.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(13.dp)) {
                        Text("下载所选清晰度")
                    }
                    Text("实际可用画质由 B 站账号权限及视频本身决定。", color = Color(0xFF87939B), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        notice?.let {
            Surface(color = Color(0xFFEAF7FC), shape = RoundedCornerShape(12.dp)) {
                Text(it, Modifier.fillMaxWidth().padding(13.dp), color = Color(0xFF007FA9), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun QuickInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color(0xFF87939B), style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DownloadsContent(modifier: Modifier, records: List<DownloadRecord>, refreshing: Boolean, onRefresh: () -> Unit, onRemove: (Long) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("下载任务", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("查看系统下载服务中的任务状态", color = Color(0xFF78848D))
        Button(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
            if (refreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("刷新任务状态")
        }
        if (records.isEmpty()) EmptyPanel("暂无下载记录", "解析视频并点击下载后，任务会显示在这里。")
        else records.forEach { record ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(record.title, fontWeight = FontWeight.SemiBold)
                    Text(downloadStatus(record.status), color = Color(0xFF008DBD), style = MaterialTheme.typography.bodySmall)
                    if (record.totalBytes > 0) Text("${record.downloadedBytes / 1024} / ${record.totalBytes / 1024} KB", color = Color(0xFF78848D), style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onRemove(record.id) }) { Text("移除任务") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourcesContent(modifier: Modifier, count: Int) {
    Column(modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("本地资源", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("集中管理下载完成的视频、音频和其他资源", color = Color(0xFF78848D))
        EmptyPanel("资源管理模块正在搭建", "当前已接入系统下载任务查询。下一步会加入本地文件扫描、分类、播放、导出和删除。")
        Text("本次会话已记录的任务：$count", color = Color(0xFF78848D), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProfileContent(modifier: Modifier) {
    Column(modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFEAF7FC), shape = CircleShape, modifier = Modifier.size(54.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("B", color = Color(0xFF008DBD), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("游客模式", fontWeight = FontWeight.Bold)
                    Text("无需登录即可使用基础下载功能", color = Color(0xFF78848D), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        EmptyPanel("账号登录尚未接入", "后续按 BVD 的账号模块思路实现授权状态、扫码登录和个人数据。不会在未完成时显示虚假的登录成功状态。")
    }
}

@Composable
private fun EmptyPanel(title: String, description: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("▤", color = Color(0xFF00A1D6), style = MaterialTheme.typography.headlineLarge)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = Color(0xFF78848D), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun downloadStatus(status: Int): String = when (status) {
    DownloadManager.STATUS_SUCCESSFUL -> "已完成"
    DownloadManager.STATUS_FAILED -> "下载失败"
    DownloadManager.STATUS_RUNNING -> "正在下载"
    DownloadManager.STATUS_PAUSED -> "已暂停"
    DownloadManager.STATUS_PENDING -> "等待下载"
    else -> "处理中"
}
