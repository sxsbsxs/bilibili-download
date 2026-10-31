package com.sxsbsxs.bilibilidownload

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

class PlayerActivity : Activity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var speedButton: Button
    private var speedIndex = 0
    private var pipEnabled = true
    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO, false)
        pipEnabled = intent.getBooleanExtra(EXTRA_PIP, true)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "BiliDownload 播放器" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff101116.toInt())
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 12, 12, 12)
        }
        titleView = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(0xffffffff.toInt())
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val close = Button(this).apply {
            text = "完成"
            setOnClickListener { finish() }
        }
        speedButton = Button(this).apply {
            text = "1.0×"
            setOnClickListener { cycleSpeed() }
        }
        top.addView(titleView)
        top.addView(speedButton)
        top.addView(close)
        root.addView(top, LinearLayout.LayoutParams(-1, -2))

        if (audioOnly) {
            val audioPanel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(28, 40, 28, 40)
                setBackgroundColor(0xff191b23.toInt())
            }
            val glyph = TextView(this).apply {
                text = "♫"
                textSize = 72f
                setTextColor(0xffff6685.toInt())
                gravity = android.view.Gravity.CENTER
            }
            val hint = TextView(this).apply {
                text = "音频播放中 · 退出页面后可继续播放"
                textSize = 13f
                setTextColor(0xffb7bac5.toInt())
                gravity = android.view.Gravity.CENTER
            }
            audioPanel.addView(glyph)
            audioPanel.addView(hint)
            root.addView(audioPanel, LinearLayout.LayoutParams(-1, 0, 1f))
            playerView = PlayerView(this).apply {
                useController = true
                visibility = View.GONE
            }
            root.addView(playerView, LinearLayout.LayoutParams(1, 1))
        } else {
            playerView = PlayerView(this).apply {
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            }
            root.addView(playerView)
        }
        setContentView(root)

        val uri = intent.getStringExtra(EXTRA_URI)
        if (uri.isNullOrBlank()) {
            titleView.text = "无法播放：文件地址为空"
            return
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    playerView.player = mediaController
                    mediaController.setMediaItem(MediaItem.fromUri(uri))
                    mediaController.prepare()
                    mediaController.playWhenReady = true
                } catch (error: Exception) {
                    titleView.text = "播放器启动失败：" + (error.message ?: "未知错误")
                }
            }, { command -> runOnUiThread(command) })
        }
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % speeds.size
        val speed = speeds[speedIndex]
        controller?.setPlaybackSpeed(speed)
        speedButton.text = String.format(java.util.Locale.US, "%.2f×", speed).replace(".00", "")
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !intent.getBooleanExtra(EXTRA_AUDIO, false) && controller?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onDestroy() {
        playerView.player = null
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "media_uri"
        const val EXTRA_TITLE = "media_title"
        const val EXTRA_AUDIO = "audio_only"
        const val EXTRA_PIP = "pip_enabled"
    }
}
