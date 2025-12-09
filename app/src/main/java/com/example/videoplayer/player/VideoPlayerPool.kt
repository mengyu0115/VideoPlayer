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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 双播放器池 - 零延迟切换架构
 *
 * 核心策略：
 * 1. 维护两个 ExoPlayer 实例：currentPlayer（正在播放）和 preloadPlayer（预加载下一个）
 * 2. 当播放视频 N 时，preloadPlayer 自动准备视频 N+1（prepare 但不 play）
 * 3. 滑动到视频 N+1 时，角色互换：preloadPlayer 变成 currentPlayer 并播放
 * 4. 旧的 currentPlayer 变成新的 preloadPlayer，停止并准备视频 N+2
 * 5. 无缝切换，消除物理延迟
 */
class VideoPlayerPool private constructor(context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "VideoPlayerPool"

        @Volatile
        private var instance: VideoPlayerPool? = null

        fun getInstance(context: Context): VideoPlayerPool {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerPool(context.applicationContext).also {
                    instance = it
                    Log.d(TAG, "getInstance: 创建 VideoPlayerPool 实例")
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

    // 双播放器实例
    private var currentPlayer: ExoPlayer
    private var preloadPlayer: ExoPlayer

    // 双 PlayerView 实例
    private var currentPlayerView: PlayerView
    private var preloadPlayerView: PlayerView

    // 当前播放的容器和位置
    private var currentParent: ViewGroup? = null
    private var currentPosition: Int = -1

    // 预加载的容器和位置
    private var preloadParent: ViewGroup? = null
    private var preloadPosition: Int = -1

    // 首帧渲染监听器
    private var firstFrameListener: OnFirstFrameRenderedListener? = null

    // 角色标记
    private enum class PlayerRole {
        CURRENT,  // 当前播放
        PRELOAD   // 预加载
    }

    init {
        Log.d(TAG, "init: 初始化双播放器池")

        // 初始化缓存系统
        CacheManager.initialize(context)

        Log.d(TAG, "init: 创建第一个 ExoPlayer（currentPlayer）")
        currentPlayer = createPlayer(context, PlayerRole.CURRENT)

        Log.d(TAG, "init: 创建第二个 ExoPlayer（preloadPlayer）")
        preloadPlayer = createPlayer(context, PlayerRole.PRELOAD)

        Log.d(TAG, "init: 创建 currentPlayerView")
        currentPlayerView = createPlayerView(context, currentPlayer)

        Log.d(TAG, "init: 创建 preloadPlayerView")
        preloadPlayerView = createPlayerView(context, preloadPlayer)

        Log.d(TAG, "init: ========== 双播放器池初始化完成 ==========")
    }

    /**
     * 创建 ExoPlayer 实例
     * 每个播放器拥有独立的 LoadControl，避免共享冲突
     */
    @OptIn(UnstableApi::class)
    private fun createPlayer(
        context: Context,
        role: PlayerRole
    ): ExoPlayer {
        val roleTag = if (role == PlayerRole.CURRENT) "CURRENT" else "PRELOAD"
        Log.d(TAG, "createPlayer: 创建 Player（角色=$roleTag）")

        // 🔧 迭代11优化：专项缓冲策略调整（局域网/HTTP视频流）
        // 参数说明：
        // minBufferMs: 最小缓冲时间（3秒） - 抗抖动，但不过度占用内存
        // maxBufferMs: 最大缓冲时间（8秒） - 平衡流畅度与内存占用
        // bufferForPlaybackMs: 起播阈值（500ms） - 保证秒开体验
        // bufferForPlaybackAfterRebufferMs: 卡顿后恢复阈值（2秒） - 防止反复卡顿
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3000,   // minBufferMs: 3秒（提高到3秒以抗抖动）
                8000,   // maxBufferMs: 8秒（允许缓存更多内容，但避免过度）
                500,    // bufferForPlaybackMs: 500ms（保证秒开）
                2000    // bufferForPlaybackAfterRebufferMs: 2秒（卡顿后多加载一会儿）
            )
            .setPrioritizeTimeOverSizeThresholds(true)  // 优先保证时长满足
            .build()

        Log.d(TAG, "createPlayer: [$roleTag] 已创建独立的 LoadControl")

        // 🔧 迭代11优化：配置渲染器优化
        // 关闭扩展渲染器，使用默认渲染器（更稳定）
        // 启用解码器降级，增加稳定性（硬解失败时自动切换软解）
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        Log.d(TAG, "createPlayer: [$roleTag] 已创建 RenderersFactory（渲染优化）")

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)  // 应用渲染器优化
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    CacheManager.getCacheDataSourceFactory()
                )
            )
            .setLoadControl(loadControl)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val state = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        Log.d(TAG, "[$roleTag] onPlaybackStateChanged: $state")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "[$roleTag] onIsPlayingChanged: ${if (isPlaying) "播放中" else "暂停"}")
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "[$roleTag] onPlayerError: ${error.message}", error)
                    }

                    override fun onRenderedFirstFrame() {
                        // 只有 currentPlayer 触发首帧渲染回调
                        if (this@apply == currentPlayer) {
                            Log.d(TAG, "[$roleTag] ========== 首帧渲染 ==========")
                            Log.d(TAG, "[$roleTag] onRenderedFirstFrame: ✅ 视频首帧已渲染！")
                            firstFrameListener?.onFirstFrameRendered()
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Log.d(TAG, "[$roleTag] onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
                        adjustResizeMode(
                            if (this@apply == currentPlayer) currentPlayerView else preloadPlayerView,
                            videoSize.width,
                            videoSize.height
                        )
                    }
                })

                repeatMode = Player.REPEAT_MODE_ONE
                Log.d(TAG, "createPlayer: Player（$roleTag）创建完成")
            }
    }

    /**
     * 创建 PlayerView
     */
    @OptIn(UnstableApi::class)
    private fun createPlayerView(context: Context, player: ExoPlayer): PlayerView {
        return PlayerView(context).apply {
            this.player = player
            useController = false
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    /**
     * 播放视频（核心方法）
     *
     * @param container 目标容器
     * @param videoUrl 视频 URL
     * @param position 视频位置
     * @param nextVideoUrl 下一个视频 URL（用于预加载）
     * @param nextPosition 下一个视频位置
     * @param onFirstFrameRendered 首帧渲染回调
     */
    fun playVideo(
        container: ViewGroup,
        videoUrl: String,
        position: Int,
        nextVideoUrl: String? = null,
        nextPosition: Int = -1,
        onFirstFrameRendered: OnFirstFrameRenderedListener? = null
    ) {
        Log.d(TAG, "========== playVideo 调用 ==========")
        Log.d(TAG, "playVideo: position=$position, url=$videoUrl")
        Log.d(TAG, "playVideo: nextPosition=$nextPosition, nextUrl=$nextVideoUrl")

        firstFrameListener = onFirstFrameRendered

        // 判断是否可以直接使用预加载的播放器
        if (position == preloadPosition && preloadPosition != -1) {
            Log.d(TAG, "playVideo: ✅ 命中预加载！执行零延迟切换")
            switchToPreloadPlayer(container, position, nextVideoUrl, nextPosition)
        } else {
            Log.d(TAG, "playVideo: 未命中预加载，使用 currentPlayer 正常播放")
            playWithCurrentPlayer(container, videoUrl, position, nextVideoUrl, nextPosition)
        }
    }

    /**
     * 使用 currentPlayer 正常播放
     */
    private fun playWithCurrentPlayer(
        container: ViewGroup,
        videoUrl: String,
        position: Int,
        nextVideoUrl: String?,
        nextPosition: Int
    ) {
        Log.d(TAG, "playWithCurrentPlayer: 开始")

        // 从旧容器移除
        currentParent?.removeView(currentPlayerView)

        // 添加到新容器
        container.addView(currentPlayerView)
        currentParent = container
        currentPosition = position

        // 设置媒体源并播放
        Log.d(TAG, "playWithCurrentPlayer: 设置媒体源")
        val mediaItem = MediaItem.fromUri(videoUrl)
        currentPlayer.setMediaItem(mediaItem)
        currentPlayer.prepare()
        currentPlayer.play()

        Log.d(TAG, "playWithCurrentPlayer: currentPlayer 开始播放 position=$position")

        // 触发预加载
        if (nextVideoUrl != null && nextPosition != -1) {
            preloadNextVideo(nextVideoUrl, nextPosition)
        }
    }

    /**
     * 切换到预加载的播放器（零延迟）
     */
    private fun switchToPreloadPlayer(
        container: ViewGroup,
        position: Int,
        nextVideoUrl: String?,
        nextPosition: Int
    ) {
        Log.d(TAG, "switchToPreloadPlayer: ========== 开始角色互换 ==========")

        // 互换播放器角色
        val tempPlayer = currentPlayer
        val tempPlayerView = currentPlayerView

        currentPlayer = preloadPlayer
        currentPlayerView = preloadPlayerView

        preloadPlayer = tempPlayer
        preloadPlayerView = tempPlayerView

        Log.d(TAG, "switchToPreloadPlayer: ✅ 播放器角色已互换")

        // 停止旧的 currentPlayer（现在是 preloadPlayer）
        preloadPlayer.pause()

        // 移除旧的 currentPlayerView
        currentParent?.removeView(preloadPlayerView)

        // 添加新的 currentPlayerView 到容器
        container.addView(currentPlayerView)
        currentParent = container
        currentPosition = position

        // 播放（已经 prepared）
        currentPlayer.play()

        Log.d(TAG, "switchToPreloadPlayer: ✅ 零延迟切换完成！position=$position")

        // 预加载下一个视频
        if (nextVideoUrl != null && nextPosition != -1) {
            preloadNextVideo(nextVideoUrl, nextPosition)
        }
    }

    /**
     * 预加载下一个视频
     *
     * 🔧 迭代11优化：预加载并发控制（Preload Throttling）
     * 核心策略：只有当 currentPlayer 播放稳定时才预加载，避免抢占带宽/CPU
     *
     * 判断条件：
     * 1. currentPlayer 的状态必须是 STATE_READY（已准备好）
     * 2. currentPlayer 不能处于 buffering 状态（isLoading == false）
     * 3. 如果当前视频正在卡顿缓冲，暂停预加载，把所有资源留给当前视频
     */
    private fun preloadNextVideo(videoUrl: String, position: Int) {
        Log.d(TAG, "========== 预加载下一个视频 ==========")
        Log.d(TAG, "preloadNextVideo: position=$position, url=$videoUrl")

        // 🔧 迭代11优化：检查 currentPlayer 状态，智能预加载
        val currentState = currentPlayer.playbackState
        val isCurrentBuffering = currentPlayer.isLoading

        Log.d(TAG, "preloadNextVideo: 当前播放器状态检查")
        Log.d(TAG, "  - playbackState: ${stateToString(currentState)}")
        Log.d(TAG, "  - isLoading: $isCurrentBuffering")

        // 只有当前视频播放稳定（STATE_READY 且不在缓冲）时，才执行预加载
        if (currentState != Player.STATE_READY || isCurrentBuffering) {
            Log.w(TAG, "preloadNextVideo: ⚠️ 当前视频未就绪或正在缓冲，跳过预加载")
            Log.w(TAG, "  -> 策略：把所有带宽/CPU留给当前视频，确保流畅播放")
            preloadPosition = -1  // 重置预加载位置
            return
        }

        Log.d(TAG, "preloadNextVideo: ✅ 当前视频播放稳定，允许预加载")

        // 停止 preloadPlayer
        preloadPlayer.stop()

        // 设置媒体源并 prepare（但不 play）
        val mediaItem = MediaItem.fromUri(videoUrl)
        preloadPlayer.setMediaItem(mediaItem)
        preloadPlayer.prepare()
        preloadPlayer.playWhenReady = false  // 关键：不自动播放

        preloadPosition = position

        Log.d(TAG, "preloadNextVideo: ✅ 预加载完成，position=$position 已准备就绪")
        Log.d(TAG, "preloadNextVideo: 下次滑到 position=$position 将实现零延迟切换！")
    }

    /**
     * 将播放状态转换为字符串（用于日志）
     */
    private fun stateToString(state: Int): String {
        return when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN"
        }
    }

    /**
     * 调整 ResizeMode
     */
    @OptIn(UnstableApi::class)
    private fun adjustResizeMode(playerView: PlayerView, width: Int, height: Int) {
        val resizeMode = if (height >= width) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        playerView.resizeMode = resizeMode
    }

    /**
     * 暂停播放
     */
    fun pause() {
        Log.d(TAG, "pause: 暂停 currentPlayer")
        currentPlayer.pause()
    }

    /**
     * 恢复播放
     */
    fun resume() {
        Log.d(TAG, "resume: 恢复 currentPlayer")
        currentPlayer.play()
    }

    /**
     * 获取当前播放状态
     */
    fun isPlaying(): Boolean {
        return currentPlayer.isPlaying
    }

    /**
     * 切换播放/暂停状态
     */
    fun togglePlayPause() {
        if (isPlaying()) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "release: 释放双播放器资源")
        currentPlayer.release()
        preloadPlayer.release()
        currentParent?.removeView(currentPlayerView)
        preloadParent?.removeView(preloadPlayerView)
        Log.d(TAG, "release: ✅ 资源已释放")
    }

    // ========== DefaultLifecycleObserver 实现 ==========

    override fun onPause(owner: LifecycleOwner) {
        Log.d(TAG, "onPause: 生命周期 - 暂停播放")
        pause()
    }

    override fun onResume(owner: LifecycleOwner) {
        Log.d(TAG, "onResume: 生命周期 - 恢复播放")
        resume()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.d(TAG, "onDestroy: 生命周期 - 释放播放器")
        release()
    }
}
