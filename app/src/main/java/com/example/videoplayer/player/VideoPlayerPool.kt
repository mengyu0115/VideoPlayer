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
 * 四播放器池 - 零延迟切换架构（迭代16升级）
 *
 * 核心策略升级（双播放器 → 四播放器）：
 * 1. 维护4个 ExoPlayer 实例池，支持更强大的预加载能力
 * 2. currentPlayer: 当前正在播放的视频
 * 3. nextPlayer: 预加载下一个视频（向下滑）
 * 4. prevPlayer: 预加载上一个视频（向上滑，支持回滑）
 * 5. sparePlayer: 备用播放器（用于更远距离的预加载或应急切换）
 * 6. 动态角色分配：播放器根据需要动态切换角色
 * 7. LRU 复用策略：最少使用的播放器优先被回收复用
 */
class VideoPlayerPool @OptIn(UnstableApi::class)
private constructor(context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "VideoPlayerPool"
        private const val POOL_SIZE = 4  // 播放器池大小（从2升级到4）

        @Volatile
        private var instance: VideoPlayerPool? = null

        fun getInstance(context: Context): VideoPlayerPool {
            return instance ?: synchronized(this) {
                instance ?: VideoPlayerPool(context.applicationContext).also {
                    instance = it
                    Log.d(TAG, "getInstance: 创建 VideoPlayerPool 实例（POOL_SIZE=$POOL_SIZE）")
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

    // 🔥 四播放器池实例（从双播放器升级）
    private val playerPool: MutableList<ExoPlayer> = mutableListOf()
    private val playerViewPool: MutableList<PlayerView> = mutableListOf()

    // 播放器状态映射（position → player index）
    private val positionToPlayerMap = mutableMapOf<Int, Int>()

    // 当前播放的播放器索引
    private var currentPlayerIndex: Int = 0

    // 当前播放的容器和位置
    private var currentParent: ViewGroup? = null
    private var currentPosition: Int = -1

    // 预加载位置集合（支持多个预加载）
    private val preloadPositions = mutableSetOf<Int>()

    // 首帧渲染监听器
    private var firstFrameListener: OnFirstFrameRenderedListener? = null

    init {
        Log.d(TAG, "init: ========== 初始化四播放器池 ==========")

        // 初始化缓存系统
        CacheManager.initialize(context)

        // 创建4个播放器实例
        for (i in 0 until POOL_SIZE) {
            Log.d(TAG, "init: 创建第 ${i + 1} 个 ExoPlayer")
            val player = createPlayer(context, i)
            playerPool.add(player)

            Log.d(TAG, "init: 创建第 ${i + 1} 个 PlayerView")
            val playerView = createPlayerView(context, player, i)
            playerViewPool.add(playerView)
        }

        Log.d(TAG, "init: ========== 四播放器池初始化完成 ==========")
        Log.d(TAG, "init:   总播放器数量: $POOL_SIZE")
        Log.d(TAG, "init:   当前播放器索引: $currentPlayerIndex")
    }

    /**
     * 创建 ExoPlayer 实例
     * 每个播放器拥有独立的 LoadControl，避免共享冲突
     *
     * @param playerIndex 播放器索引（0-3）
     */
    @OptIn(UnstableApi::class)
    private fun createPlayer(context: Context, playerIndex: Int): ExoPlayer {
        Log.d(TAG, "createPlayer: 创建 Player #$playerIndex")

        // 迭代11优化：专项缓冲策略调整（局域网/HTTP视频流）
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

        Log.d(TAG, "createPlayer: [Player #$playerIndex] 已创建独立的 LoadControl")

        // 迭代11优化：配置渲染器优化
        // 关闭扩展渲染器，使用默认渲染器（更稳定）
        // 启用解码器降级，增加稳定性（硬解失败时自动切换软解）
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)

        Log.d(TAG, "createPlayer: [Player #$playerIndex] 已创建 RenderersFactory（渲染优化）")

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
                        Log.d(TAG, "[Player #$playerIndex] onPlaybackStateChanged: $state")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "[Player #$playerIndex] onIsPlayingChanged: ${if (isPlaying) "播放中" else "暂停"}")
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "[Player #$playerIndex] onPlayerError: ${error.message}", error)
                    }

                    override fun onRenderedFirstFrame() {
                        // 只有当前播放的播放器触发首帧渲染回调
                        if (playerIndex == currentPlayerIndex) {
                            Log.d(TAG, "[Player #$playerIndex] ========== 首帧渲染 ==========")
                            Log.d(TAG, "[Player #$playerIndex] onRenderedFirstFrame: 视频首帧已渲染！")
                            firstFrameListener?.onFirstFrameRendered()
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Log.d(TAG, "[Player #$playerIndex] onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
                        adjustResizeMode(
                            playerViewPool[playerIndex],
                            videoSize.width,
                            videoSize.height
                        )
                    }
                })

                repeatMode = Player.REPEAT_MODE_ONE
                Log.d(TAG, "createPlayer: Player #$playerIndex 创建完成")
            }
    }

    /**
     * 创建 PlayerView
     *
     * @param playerIndex 播放器索引
     */
    @OptIn(UnstableApi::class)
    private fun createPlayerView(context: Context, player: ExoPlayer, playerIndex: Int): PlayerView {
        return PlayerView(context).apply {
            this.player = player
            useController = false
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            Log.d(TAG, "createPlayerView: PlayerView #$playerIndex 创建完成")
        }
    }

    /**
     * 播放视频（核心方法 - 简化版）
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

        // 检查是否已有播放器为这个 position 预加载
        val preloadedPlayerIndex = positionToPlayerMap[position]

        if (preloadedPlayerIndex != null && preloadPositions.contains(position)) {
            Log.d(TAG, "playVideo: 命中预加载！使用 Player #$preloadedPlayerIndex")
            switchToPlayer(preloadedPlayerIndex, container, position)
        } else {
            Log.d(TAG, "playVideo: 使用当前播放器 #$currentPlayerIndex")
            playWithPlayer(currentPlayerIndex, container, videoUrl, position)
        }

        // 预加载下一个视频
        if (nextVideoUrl != null && nextPosition != -1) {
            preloadVideo(nextVideoUrl, nextPosition)
        }
    }

    /**
     * 迭代16优化：准备视频并显示首帧，但不播放
     *
     * 用于在item可见时提前准备视频，让用户在滑动过程中就能看到首帧
     *
     * @param container 目标容器
     * @param videoUrl 视频 URL
     * @param position 视频位置
     */
    fun prepareVideoForPreview(
        container: ViewGroup,
        videoUrl: String,
        position: Int
    ) {
        // 如果当前位置就是这个position，不需要prepare（已经在播放了）
        if (currentPosition == position) {
            Log.d(TAG, "prepareVideoForPreview: position=$position 已经是当前位置，跳过")
            return
        }

        // 检查是否已经为这个position准备过播放器
        val existingPlayerIndex = positionToPlayerMap[position]
        if (existingPlayerIndex != null) {
            Log.d(TAG, "prepareVideoForPreview: position=$position 已经有播放器 #$existingPlayerIndex，跳过")
            return
        }

        // 检查当前播放器状态，只有稳定时才准备预览
        val currentPlayer = playerPool[currentPlayerIndex]
        if (currentPlayer.playbackState != Player.STATE_READY || currentPlayer.isLoading) {
            Log.w(TAG, "prepareVideoForPreview: 当前视频未就绪，跳过预览准备")
            return
        }

        // 找一个空闲的播放器
        val freePlayerIndex = findFreePlayer()
        if (freePlayerIndex == -1) {
            Log.w(TAG, "prepareVideoForPreview: 无空闲播放器，跳过预览准备")
            return
        }

        Log.d(TAG, "========== prepareVideoForPreview ==========")
        Log.d(TAG, "prepareVideoForPreview: position=$position, url=$videoUrl, player=#$freePlayerIndex")

        val player = playerPool[freePlayerIndex]
        val playerView = playerViewPool[freePlayerIndex]

        // 停止播放器
        player.stop()

        // 从旧容器移除
        (playerView.parent as? ViewGroup)?.removeView(playerView)

        // 添加到新容器
        container.addView(playerView)

        // 设置媒体源并 prepare（但不播放）
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false  // 关键：不自动播放，停在首帧

        // 记录映射
        positionToPlayerMap[position] = freePlayerIndex
        preloadPositions.add(position)

        Log.d(TAG, "prepareVideoForPreview: Player #$freePlayerIndex 已准备首帧预览 position=$position")
    }

    /**
     * 使用指定播放器播放视频
     */
    private fun playWithPlayer(
        playerIndex: Int,
        container: ViewGroup,
        videoUrl: String,
        position: Int
    ) {
        Log.d(TAG, "playWithPlayer: Player #$playerIndex, position=$position")

        val player = playerPool[playerIndex]
        val playerView = playerViewPool[playerIndex]

        // 从旧容器移除
        currentParent?.removeView(playerView)

        // 添加到新容器
        container.addView(playerView)
        currentParent = container
        currentPosition = position
        currentPlayerIndex = playerIndex

        // 设置媒体源并播放
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        // 更新映射
        positionToPlayerMap[position] = playerIndex
        preloadPositions.remove(position)  // 从预加载集合移除（现在是正在播放）

        Log.d(TAG, "playWithPlayer:  Player #$playerIndex 开始播放 position=$position")
    }

    /**
     * 切换到指定播放器（零延迟）
     */
    private fun switchToPlayer(
        playerIndex: Int,
        container: ViewGroup,
        position: Int
    ) {
        Log.d(TAG, "switchToPlayer: 切换到 Player #$playerIndex")

        // 暂停当前播放器
        playerPool[currentPlayerIndex].pause()

        // 更新当前播放器索引
        currentPlayerIndex = playerIndex

        val player = playerPool[playerIndex]
        val playerView = playerViewPool[playerIndex]

        // 从旧容器移除
        currentParent?.removeView(playerView)

        // 添加到新容器
        container.addView(playerView)
        currentParent = container
        currentPosition = position

        // 播放（已经 prepared）
        player.play()

        // 从预加载集合移除
        preloadPositions.remove(position)

        Log.d(TAG, "switchToPlayer: 零延迟切换完成！")
    }

    /**
     * 预加载视频
     */
    private fun preloadVideo(videoUrl: String, position: Int) {
        Log.d(TAG, "========== 预加载视频 ==========")
        Log.d(TAG, "preloadVideo: position=$position, url=$videoUrl")

        // 检查当前播放器状态
        val currentPlayer = playerPool[currentPlayerIndex]
        if (currentPlayer.playbackState != Player.STATE_READY || currentPlayer.isLoading) {
            Log.w(TAG, "preloadVideo: 当前视频未就绪，跳过预加载")
            return
        }

        // 找一个空闲的播放器
        val freePlayerIndex = findFreePlayer()
        if (freePlayerIndex == -1) {
            Log.w(TAG, "preloadVideo: 无空闲播放器，跳过预加载")
            return
        }

        val player = playerPool[freePlayerIndex]

        // 停止播放器
        player.stop()

        // 设置媒体源并 prepare（但不 play）
        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false  // 关键：不自动播放

        // 记录预加载
        positionToPlayerMap[position] = freePlayerIndex
        preloadPositions.add(position)

        Log.d(TAG, "preloadVideo: Player #$freePlayerIndex 预加载完成 position=$position")
    }

    /**
     * 查找空闲播放器（LRU策略）
     * 返回不是当前播放且未被预加载的播放器索引
     */
    private fun findFreePlayer(): Int {
        for (i in 0 until POOL_SIZE) {
            if (i != currentPlayerIndex && !positionToPlayerMap.containsValue(i)) {
                return i
            }
        }

        // 如果所有播放器都被占用，回收最早的预加载播放器
        if (preloadPositions.isNotEmpty()) {
            val oldestPosition = preloadPositions.first()
            val recyclePlayerIndex = positionToPlayerMap[oldestPosition]
            if (recyclePlayerIndex != null && recyclePlayerIndex != currentPlayerIndex) {
                Log.d(TAG, "findFreePlayer: 回收 Player #$recyclePlayerIndex（LRU）")
                positionToPlayerMap.remove(oldestPosition)
                preloadPositions.remove(oldestPosition)
                return recyclePlayerIndex
            }
        }

        return -1
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
        Log.d(TAG, "pause: 暂停 Player #$currentPlayerIndex")
        playerPool[currentPlayerIndex].pause()
    }

    /**
     * 恢复播放
     */
    fun resume() {
        Log.d(TAG, "resume: 恢复 Player #$currentPlayerIndex")
        playerPool[currentPlayerIndex].play()
    }

    /**
     * 获取当前播放状态
     */
    fun isPlaying(): Boolean {
        return playerPool[currentPlayerIndex].isPlaying
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
     * 释放资源（释放所有4个播放器）
     */
    fun release() {
        Log.d(TAG, "release: ========== 释放四播放器池资源 ==========")

        for (i in 0 until POOL_SIZE) {
            Log.d(TAG, "release: 释放 Player #$i")
            playerPool[i].release()
            currentParent?.removeView(playerViewPool[i])
        }

        playerPool.clear()
        playerViewPool.clear()
        positionToPlayerMap.clear()
        preloadPositions.clear()

        Log.d(TAG, "release: 所有播放器资源已释放")
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
        Log.d(TAG, "onDestroy: 生命周期 - 释放播放器池")
        release()
    }
}
