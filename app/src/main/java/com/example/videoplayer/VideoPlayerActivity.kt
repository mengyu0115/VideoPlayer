package com.example.videoplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.databinding.ActivityVideoPlayerBinding
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.ui.CommentBottomSheet
import com.example.videoplayer.utils.setupImmersiveUI
import com.example.videoplayer.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

/**
 * 视频播放 Activity
 *
 * 用途：从瀑布流点击视频封面后进入此页面播放视频
 * 特点：
 * - 全屏播放
 * - UI 样式与 VideoFragment 一致
 * - 包含所有社交功能（点赞、评论、收藏、关注）
 */
class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoPlayerActivity"
        const val EXTRA_VIDEO_ID = "video_id"
    }

    private lateinit var binding: ActivityVideoPlayerBinding
    private val viewModel: VideoViewModel by viewModels()

    private var currentVideo: VideoEntity? = null
    private var player: ExoPlayer? = null
    private var isPlaying = false
    private var hasInitializedPlayer = false  // 标志位：防止重复初始化播放器

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity 开始创建")

        // 设置沉浸式 UI
        setupImmersiveUI()

        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传入的视频 ID
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (videoId == null) {
            Log.e(TAG, "onCreate: 未传入 videoId，退出 Activity")
            Toast.makeText(this, "视频加载失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Log.d(TAG, "onCreate: videoId=$videoId")
        setupUI()
        loadVideo(videoId)
    }

    /**
     * 配置 UI 事件
     */
    private fun setupUI() {
        // 返回按钮
        binding.ivBack.setOnClickListener {
            Log.d(TAG, "setupUI: 点击返回按钮")
            finish()
        }

        // 视频点击（播放/暂停）
        binding.videoContainer.setOnClickListener {
            togglePlayPause()
        }

        // 点赞按钮
        binding.ivLike.setOnClickListener {
            currentVideo?.let { video ->
                val newLiked = !video.isLiked
                viewModel.toggleLike(video.id, newLiked)
                Log.d(TAG, "setupUI: 点赞 videoId=${video.id}, newLiked=$newLiked")
            }
        }

        // 评论按钮
        binding.ivComment.setOnClickListener {
            currentVideo?.let { video ->
                Log.d(TAG, "setupUI: 打开评论 videoId=${video.id}")
                val bottomSheet = CommentBottomSheet.newInstance(video.id)
                bottomSheet.show(supportFragmentManager, "CommentBottomSheet")
            }
        }

        // 收藏按钮
        binding.ivFavorite.setOnClickListener {
            currentVideo?.let { video ->
                val newFavorite = !video.isFavorite
                viewModel.toggleFavorite(video.id, newFavorite)
                Log.d(TAG, "setupUI: 收藏 videoId=${video.id}, newFavorite=$newFavorite")
            }
        }

        // 关注按钮
        binding.ivFollow.setOnClickListener {
            currentVideo?.let { video ->
                Log.d(TAG, "setupUI: 关注 authorId=${video.authorId}")
                viewModel.toggleFollow(
                    userId = video.authorId,
                    userName = video.authorName,
                    avatarUrl = video.authorAvatarUrl
                ) { isFollowing ->
                    Log.d(TAG, "setupUI: 关注状态更新 isFollowing=$isFollowing")
                    updateFollowButton(isFollowing)
                }
            }
        }

        // 用户头像点击
        binding.ivUserAvatar.setOnClickListener {
            // TODO: 跳转到用户主页
            Toast.makeText(this, "跳转到用户主页（待实现）", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 加载视频数据
     */
    private fun loadVideo(videoId: String) {
        Log.d(TAG, "loadVideo: 开始加载视频 videoId=$videoId")

        // 观察视频列表，找到对应视频
        viewModel.videoList.observe(this) { videos ->
            val video = videos.find { it.id == videoId }
            if (video != null) {
                currentVideo = video

                if (!hasInitializedPlayer) {
                    // 第一次加载：初始化播放器
                    Log.d(TAG, "loadVideo: 首次加载视频 title=${video.title}")
                    bindVideoData(video)
                    initPlayer(video)
                    hasInitializedPlayer = true
                } else {
                    // 后续更新：仅更新UI数据，不重新初始化播放器
                    Log.d(TAG, "loadVideo: 更新视频数据 title=${video.title}")
                    updateVideoData(video)
                }
            } else {
                Log.e(TAG, "loadVideo: 未找到视频 videoId=$videoId")
                Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * 绑定视频数据到 UI
     */
    private fun bindVideoData(video: VideoEntity) {
        Log.d(TAG, "bindVideoData: title=${video.title}")

        // 显示封面
        binding.ivCover.load(video.coverUrl) {
            crossfade(true)
        }

        // 用户信息
        binding.tvUserName.text = video.authorName
        binding.ivUserAvatar.load(video.authorAvatarUrl) {
            crossfade(true)
            transformations(CircleCropTransformation())
        }

        // 视频描述
        binding.tvDescription.text = video.description

        // 点赞数
        binding.tvLikeCount.text = formatCount(video.likeCount)
        binding.ivLike.isSelected = video.isLiked

        // 评论数
        binding.tvCommentCount.text = formatCount(video.commentCount)

        // 收藏按钮
        binding.ivFavorite.isSelected = video.isFavorite

        // 检查关注状态
        viewModel.checkIsFollowing(video.authorId) { isFollowing ->
            updateFollowButton(isFollowing)
        }
    }

    /**
     * 更新视频数据（不重新初始化播放器）
     * 用于点赞、收藏等操作后更新UI，保持播放器状态
     */
    private fun updateVideoData(video: VideoEntity) {
        Log.d(TAG, "updateVideoData: 更新UI数据")

        // 更新点赞状态和数量
        binding.tvLikeCount.text = formatCount(video.likeCount)
        binding.ivLike.isSelected = video.isLiked

        // 更新评论数
        binding.tvCommentCount.text = formatCount(video.commentCount)

        // 更新收藏状态
        binding.ivFavorite.isSelected = video.isFavorite

        // 检查关注状态
        viewModel.checkIsFollowing(video.authorId) { isFollowing ->
            updateFollowButton(isFollowing)
        }
    }

    /**
     * 初始化播放器
     */
    private fun initPlayer(video: VideoEntity) {
        Log.d(TAG, "initPlayer: 开始初始化播放器 videoUrl=${video.videoUrl}")

        // 创建 ExoPlayer 实例
        player = ExoPlayer.Builder(this).build()

        // 创建 PlayerView 并绑定播放器
        val playerView = PlayerView(this).apply {
            player = this@VideoPlayerActivity.player
            useController = false // 隐藏默认控制器
        }

        // 添加到容器
        binding.videoContainer.removeAllViews()
        binding.videoContainer.addView(playerView)

        // 设置视频源
        val mediaItem = MediaItem.fromUri(video.videoUrl)
        player?.setMediaItem(mediaItem)
        player?.prepare()

        // 监听播放状态
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        Log.d(TAG, "onPlaybackStateChanged: 准备就绪")
                        // 隐藏封面，开始播放
                        binding.ivCover.visibility = View.GONE
                        player?.play()
                        isPlaying = true
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "onPlaybackStateChanged: 播放结束")
                        // 显示封面，重置播放器
                        binding.ivCover.visibility = View.VISIBLE
                        player?.seekTo(0)
                        player?.pause()
                        isPlaying = false
                    }
                }
            }
        })

        Log.d(TAG, "initPlayer: 播放器初始化完成")
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                Log.d(TAG, "togglePlayPause: 暂停播放")
                it.pause()
                isPlaying = false
            } else {
                Log.d(TAG, "togglePlayPause: 继续播放")
                binding.ivCover.visibility = View.GONE
                it.play()
                isPlaying = true
            }
        }
    }

    /**
     * 更新关注按钮显示
     */
    private fun updateFollowButton(isFollowing: Boolean) {
        binding.ivFollow.visibility = if (isFollowing) View.GONE else View.VISIBLE
    }

    /**
     * 格式化数字（1000 -> 1k）
     */
    private fun formatCount(count: Int): String {
        return when {
            count >= 10000 -> String.format("%.1fw", count / 10000.0)
            count >= 1000 -> String.format("%.1fk", count / 1000.0)
            else -> count.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity 恢复")
        // 恢复播放
        if (isPlaying) {
            player?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Activity 暂停")
        // 暂停播放
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity 销毁")
        // 释放播放器
        player?.release()
        player = null
    }
}
