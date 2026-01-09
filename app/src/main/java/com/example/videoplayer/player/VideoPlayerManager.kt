package com.example.videoplayer.player

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 视频播放器管理类 - 单例模式
 * 核心职责：
 * 1. 持有全局唯一的 ExoPlayer 实例
 * 2. 管理 PlayerView 的动态挂载
 * 3. 处理播放器生命周期
 * 4. 根据视频宽高比动态调整显示模式
 * 5. 提供首帧渲染回调（秒开优化）
 */
class VideoPlayerManager @OptIn(UnstableApi::class)
private constructor(context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "VideoPlayerManager"

        @Volatile
        private var instance: VideoPlayerManager? = null

        fun getInstance(context: Context): VideoPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerManager(context.applicationContext).also {
                    instance = it
                    Log.d(TAG, "getInstance: 创建 VideoPlayerManager 单例实例")
                }
            }
        }
    }

    /**
     * 首帧渲染回调接口
     */
    interface OnFirstFrameRenderedListener {
        fun onFirstFrameRendered()
    }

    // 全局唯一的 ExoPlayer 实例
    private val player: ExoPlayer

    // 全局唯一的 PlayerView
    private val playerView: PlayerView

    // 当前正在播放的 ViewHolder 容器
    private var currentParent: ViewGroup? = null

    // 当前播放的视频位置
    private var currentPosition: Int = -1

    // 首帧渲染回调监听器
    private var firstFrameListener: OnFirstFrameRenderedListener? = null

    init {
        Log.d(TAG, "init: 初始化 VideoPlayerManager")

        // 初始化缓存系统
        Log.d(TAG, "init: 初始化缓存系统")
        CacheManager.initialize(context)

        // 创建激进的 LoadControl 配置（秒开优化）
        Log.d(TAG, "init: 配置激进的 LoadControl（秒开优化）")
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // minBufferMs: 最小缓冲 1 秒
                2000,   // maxBufferMs: 最大缓冲 2 秒（短视频不需要太长）
                500,    // bufferForPlaybackMs: 起播缓冲 0.5 秒就开始播放！
                1000    // bufferForPlaybackAfterRebufferMs: 重新缓冲后 1 秒继续
            )
            .build()

        Log.d(TAG, "init: LoadControl 配置完成 - 起播缓冲=500ms（激进策略）")

        // 创建 ExoPlayer 实例（使用 CacheDataSource + 激进 LoadControl）
        Log.d(TAG, "init: 创建 ExoPlayer 实例（带缓存支持 + 秒开优化）")
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    CacheManager.getCacheDataSourceFactory()
                )
            )
            .setLoadControl(loadControl)  // 注入自定义 LoadControl
            .build().apply {
            // 设置播放器监听器
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val state = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    Log.d(TAG, "onPlaybackStateChanged: 播放状态变化 -> $state")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Log.d(TAG, "onIsPlayingChanged: 播放状态 -> ${if (isPlaying) "播放中" else "暂停"}")
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "onPlayerError: 播放器错误 -> ${error.message}", error)
                }

                override fun onRenderedFirstFrame() {
                    Log.d(TAG, "========== 首帧渲染 ==========")
                    Log.d(TAG, "onRenderedFirstFrame:  视频首帧已渲染到屏幕！")
                    Log.d(TAG, "onRenderedFirstFrame: 触发回调，可以隐藏封面图了")

                    // 通知外部隐藏封面图
                    firstFrameListener?.onFirstFrameRendered()

                    Log.d(TAG, "onRenderedFirstFrame: ========== 首帧渲染回调完成 ==========")
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    Log.d(TAG, "========== 视频尺寸变化 ==========")
                    Log.d(TAG, "onVideoSizeChanged: 视频宽度 = ${videoSize.width}")
                    Log.d(TAG, "onVideoSizeChanged: 视频高度 = ${videoSize.height}")
                    Log.d(TAG, "onVideoSizeChanged: 像素宽高比 = ${videoSize.pixelWidthHeightRatio}")

                    // 计算宽高比
                    val aspectRatio = if (videoSize.height > 0) {
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                    } else {
                        0f
                    }
                    Log.d(TAG, "onVideoSizeChanged: 计算宽高比 = ${String.format("%.2f", aspectRatio)}")

                    // 根据视频宽高比动态调整 ResizeMode
                    adjustResizeMode(videoSize.width, videoSize.height)
                }
            })

            // 设置循环播放
            repeatMode = Player.REPEAT_MODE_ONE
            Log.d(TAG, "init: 设置循环播放模式")
        }

        // 创建 PlayerView
        Log.d(TAG, "init: 创建 PlayerView")
        playerView = PlayerView(context).apply {
            this.player = this@VideoPlayerManager.player
            useController = false // 不显示控制器

            // 使用 SurfaceView 而非 TextureView（性能优化）
            // SurfaceView 耗电更低且帧率更稳，Android 7.0+ 已无层级问题
            // 注意：通过 XML 属性或 setShutterBackgroundColor 会自动使用 SurfaceView
            // PlayerView 默认就会根据场景选择最优的 Surface 类型
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            Log.d(TAG, "init: PlayerView 配置 - 使用默认 SurfaceView（性能优化）")

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            Log.d(TAG, "init: PlayerView 配置完成 - useController=false")
        }

        Log.d(TAG, "init: VideoPlayerManager 初始化完成")
    }

    /**
     * 将 PlayerView 挂载到指定的容器，并播放指定的视频
     * @param container 目标容器
     * @param videoUrl 视频 URL
     * @param position 视频位置
     * @param nextVideoUrl 下一个视频的 URL（用于预加载），可为 null
     * @param nextPosition 下一个视频的位置（用于预加载日志），可为 -1
     * @param onFirstFrameRendered 首帧渲染回调（秒开优化），可为 null
     */
    fun attachPlayerView(
        container: ViewGroup,
        videoUrl: String,
        position: Int,
        nextVideoUrl: String? = null,
        nextPosition: Int = -1,
        onFirstFrameRendered: OnFirstFrameRenderedListener? = null
    ) {
        Log.d(TAG, "attachPlayerView: 准备挂载播放器")
        Log.d(TAG, "attachPlayerView: position=$position, videoUrl=$videoUrl")
        Log.d(TAG, "attachPlayerView: 目标容器 hashCode=${container.hashCode()}")

        // 设置首帧渲染监听器
        firstFrameListener = onFirstFrameRendered
        if (onFirstFrameRendered != null) {
            Log.d(TAG, "attachPlayerView:  已设置首帧渲染监听器（用于隐藏封面）")
        }

        // 如果是同一个位置，不需要重新挂载
        if (currentPosition == position && currentParent == container) {
            Log.d(TAG, "attachPlayerView: 相同位置，跳过挂载")
            return
        }

        // 从旧容器中移除 PlayerView
        if (currentParent != null) {
            Log.d(TAG, "attachPlayerView: 从旧容器中移除 PlayerView, 旧容器 hashCode=${currentParent.hashCode()}")
            currentParent?.removeView(playerView)
        }

        // 添加到新容器
        Log.d(TAG, "attachPlayerView: 将 PlayerView 添加到新容器")
        container.addView(playerView)
        currentParent = container
        currentPosition = position

        // 设置视频源并播放
        Log.d(TAG, "attachPlayerView: 设置媒体源 -> $videoUrl")
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)

        Log.d(TAG, "attachPlayerView: 准备播放器")
        player.prepare()

        Log.d(TAG, "attachPlayerView: 开始播放")
        player.play()

        Log.d(TAG, "attachPlayerView: PlayerView 挂载完成，当前位置=$currentPosition")

        // 触发下一个视频的预加载
        if (nextVideoUrl != null) {
            Log.d(TAG, "attachPlayerView: 触发预加载 - 下一个视频 position=$nextPosition")
            PreloadManager.preload(nextVideoUrl, nextPosition)
        } else {
            Log.d(TAG, "attachPlayerView: 无下一个视频，跳过预加载")
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        Log.d(TAG, "pause: 暂停播放")
        player.pause()
    }

    /**
     * 恢复播放
     */
    fun resume() {
        Log.d(TAG, "resume: 恢复播放")
        player.play()
    }

    /**
     * 停止播放
     */
    fun stop() {
        Log.d(TAG, "stop: 停止播放")
        player.stop()
        if (currentParent != null) {
            Log.d(TAG, "stop: 从容器中移除 PlayerView")
            currentParent?.removeView(playerView)
            currentParent = null
        }
        currentPosition = -1
    }

    /**
     * 释放播放器资源
     */
    fun release() {
        Log.d(TAG, "release: 释放播放器资源")
        player.release()
        if (currentParent != null) {
            currentParent?.removeView(playerView)
            currentParent = null
        }
        currentPosition = -1
        Log.d(TAG, "release: 播放器资源已释放")
    }

    /**
     * 根据视频宽高动态调整 PlayerView 的 ResizeMode
     * @param width 视频宽度
     * @param height 视频高度
     */
    @OptIn(UnstableApi::class)
    private fun adjustResizeMode(width: Int, height: Int) {
        Log.d(TAG, "adjustResizeMode: ========== 开始调整 ResizeMode ==========")
        Log.d(TAG, "adjustResizeMode: 输入尺寸 - width=$width, height=$height")

        if (width <= 0 || height <= 0) {
            Log.w(TAG, "adjustResizeMode: 无效的视频尺寸，跳过调整")
            return
        }

        val resizeMode = if (height >= width) {
            // 竖屏或正方形视频：使用 ZOOM 模式填充全屏
            Log.d(TAG, "adjustResizeMode: 检测到竖屏视频 (height >= width)")
            Log.d(TAG, "adjustResizeMode: 设置 ResizeMode = RESIZE_MODE_ZOOM (裁切填充)")
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            // 横屏视频：使用 FIT 模式，宽度撑满，上下留黑
            Log.d(TAG, "adjustResizeMode: 检测到横屏视频 (width > height)")
            Log.d(TAG, "adjustResizeMode: 设置 ResizeMode = RESIZE_MODE_FIT (适应屏幕)")
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        // 应用 ResizeMode
        playerView.resizeMode = resizeMode

        val modeString = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ZOOM (裁切填充)"
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT (适应屏幕)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "FILL (拉伸填充)"
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "FIXED_WIDTH (固定宽度)"
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> "FIXED_HEIGHT (固定高度)"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "adjustResizeMode: ResizeMode 已应用 -> $modeString")
        Log.d(TAG, "adjustResizeMode: ========== ResizeMode 调整完成 ==========")
    }

    // ========== DefaultLifecycleObserver 实现 ==========

    override fun onPause(owner: LifecycleOwner) {
        Log.d(TAG, "onPause: 生命周期 - Activity 暂停，暂停播放")
        pause()
    }

    override fun onResume(owner: LifecycleOwner) {
        Log.d(TAG, "onResume: 生命周期 - Activity 恢复，恢复播放")
        resume()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy: 生命周期 - Activity 销毁，释放播放器")
        release()
    }
}
