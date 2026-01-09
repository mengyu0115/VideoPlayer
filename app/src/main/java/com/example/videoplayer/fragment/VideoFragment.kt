package com.example.videoplayer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.videoplayer.R
import com.example.videoplayer.adapter.VideoAdapter
import com.example.videoplayer.databinding.FragmentVideoBinding
import com.example.videoplayer.player.VideoPlayerPool
import com.example.videoplayer.ui.CommentBottomSheet
import com.example.videoplayer.utils.findCenterVisibleItemPosition
import com.example.videoplayer.viewmodel.VideoViewModel

/**
 * 视频Fragment
 *
 * 展示短视频流，支持上下滑动切换视频
 * 使用 ViewPager2 类似的滑动体验（PagerSnapHelper）
 */
class VideoFragment : Fragment() {

    companion object {
        private const val TAG = "VideoFragment"
    }

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels() 与 Activity 共享 ViewModel
    private val viewModel: VideoViewModel by activityViewModels()

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var playerPool: VideoPlayerPool

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Fragment 创建视图")
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: 视图创建完成，开始初始化")

        // 初始化播放器池
        playerPool = VideoPlayerPool.getInstance(requireContext())
        lifecycle.addObserver(playerPool)
        Log.d(TAG, "onViewCreated: VideoPlayerPool 已添加到生命周期观察")

        setupRecyclerView()
        observeViewModel()

        Log.d(TAG, "onViewCreated: Fragment 初始化完成")
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView: 创建 VideoAdapter")
        videoAdapter = VideoAdapter()

        // 设置社交互动回调
        setupSocialCallbacks()

        binding.recyclerView.apply {
            adapter = videoAdapter

            // 使用标准 LinearLayoutManager（垂直滚动）
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

            // PagerSnapHelper 实现一次滑一页效果
            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)

            isNestedScrollingEnabled = true

            // 滑动监听
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        Log.d(TAG, "onScrollStateChanged: 滑动停止，挂载播放器")
                        attachPlayerToCenter()
                    }
                }
            })
        }

        Log.d(TAG, "setupRecyclerView: RecyclerView 配置完成")
    }

    private fun setupSocialCallbacks() {
        // 视频点击回调（暂停/播放）
        videoAdapter.onVideoClick = {
            Log.d(TAG, "视频点击: 切换播放/暂停")
            playerPool.togglePlayPause()
        }

        // 点赞回调
        videoAdapter.onLikeClick = { videoId, isLiked ->
            Log.d(TAG, "点赞回调: videoId=$videoId, isLiked=$isLiked")
            viewModel.toggleLike(videoId, isLiked)
        }

        // 评论回调
        videoAdapter.onCommentClick = { videoId ->
            Log.d(TAG, "评论回调: videoId=$videoId")
            showCommentBottomSheet(videoId)
        }

        //  Bug修复：关注回调 - 映射虚拟ID到真实ID
        videoAdapter.onFollowClick = { userId, userName, avatarUrl ->
            // 映射虚拟ID到真实用户ID
            val realUserId = mapVirtualIdToRealId(userId)
            val realUserName = if (realUserId != userId) realUserId else userName

            Log.d(TAG, "========================================")
            Log.d(TAG, "关注回调: 原始userId=$userId → 映射后=$realUserId")
            Log.d(TAG, "关注回调: 原始userName=$userName → 映射后=$realUserName")
            Log.d(TAG, "========================================")

            viewModel.toggleFollow(realUserId, realUserName, avatarUrl) { isFollowing ->
                Log.d(TAG, "关注状态更新: realUserId=$realUserId, isFollowing=$isFollowing")
            }
        }

        // 收藏回调
        videoAdapter.onFavoriteClick = { videoId, isFavorite ->
            Log.d(TAG, "收藏回调: videoId=$videoId, isFavorite=$isFavorite")
            viewModel.toggleFavorite(videoId, isFavorite)
        }

        //  Bug修复：检查关注状态回调 - 使用真实ID
        videoAdapter.onCheckFollowStatus = { userId, callback ->
            val realUserId = mapVirtualIdToRealId(userId)
            Log.d(TAG, "检查关注状态: userId=$userId → realUserId=$realUserId")
            viewModel.checkIsFollowing(realUserId) { isFollowing ->
                Log.d(TAG, "关注状态查询结果: realUserId=$realUserId, isFollowing=$isFollowing")
                callback(isFollowing)
            }
        }
    }

    /**
     *  Bug修复：映射虚拟用户ID到真实用户ID
     * 测试数据方法，仅用于测试
     * 映射规则：
     * - user_v1 → user1
     * - user_v2 → user2
     * - user_v3 → admin
     * - user_video_0 → user1
     * - user_video_1 → user2
     * - user_video_2 → admin
     * - 其他 → 保持不变
     */
    private fun mapVirtualIdToRealId(virtualId: String): String {
        return when {
            // user_v* 格式
            virtualId == "user_v1" -> "user1"
            virtualId == "user_v2" -> "user2"
            virtualId == "user_v3" -> "admin"

            // user_video_* 格式
            virtualId == "user_video_0" -> "user1"
            virtualId == "user_video_1" -> "user2"
            virtualId == "user_video_2" -> "admin"
            virtualId == "user_video_3" -> "user1"
            virtualId == "user_video_4" -> "user2"
            virtualId == "user_video_5" -> "admin"
            virtualId == "user_video_6" -> "user1"
            virtualId == "user_video_7" -> "user2"
            virtualId == "user_video_8" -> "admin"
            virtualId == "user_video_9" -> "user1"

            // 已经是真实ID，直接返回
            else -> virtualId
        }
    }

    private fun showCommentBottomSheet(videoId: String) {
        val commentBottomSheet = CommentBottomSheet.newInstance(videoId)
        commentBottomSheet.show(parentFragmentManager, "CommentBottomSheet")
    }

    // 观察视频列表
    private fun observeViewModel() {
        Log.d(TAG, "observeViewModel: 开始观察视频列表")

        //  始终提交列表，VideoAdapter会智能处理新视频
        viewModel.videoList.observe(viewLifecycleOwner) { videos ->
            Log.d(TAG, "=========================================")
            Log.d(TAG, "observeViewModel: 收到视频列表，数量: ${videos.size}")
            Log.d(TAG, "observeViewModel: 视频详情:")
            videos.forEachIndexed { index, video ->
                Log.d(TAG, "  [$index] id=${video.id}, title=${video.title}")
            }
            Log.d(TAG, "observeViewModel: 当前 Adapter ItemCount: ${videoAdapter.getItemCount()}")
            Log.d(TAG, "=========================================")

            // 始终提交列表，Adapter会智能判断是首次加载还是新增视频
            videoAdapter.submitList(videos)

            // 只在首次加载时自动播放第一个视频
            if (videoAdapter.getItemCount() > 0 && videos.isNotEmpty()) {
                binding.recyclerView.post {
                    val centerPosition = binding.recyclerView.findCenterVisibleItemPosition()
                    if (centerPosition == -1) {
                        // 首次加载，播放第一个视频
                        Log.d(TAG, "observeViewModel: 延迟执行 - 尝试播放第一个视频")
                        attachPlayerToCenter()
                    }
                }
            }
        }
    }

    /**
     * 将播放器挂载到屏幕中心的 Item
     */
    private fun attachPlayerToCenter() {
        Log.d(TAG, "attachPlayerToCenter: ========== 开始挂载播放器 ==========")
        // 获取中心位置
        val centerPosition = binding.recyclerView.findCenterVisibleItemPosition()
        if (centerPosition == -1) {
            Log.w(TAG, "attachPlayerToCenter: 未找到中心可见的 Item")
            return
        }

        Log.d(TAG, "attachPlayerToCenter: 中心位置 = $centerPosition")
        // 获取 ViewHolder来获取视频容器
        val viewHolder = binding.recyclerView.findViewHolderForPosition(centerPosition)
        if (viewHolder == null) {
            Log.w(TAG, "attachPlayerToCenter: 未找到 ViewHolder, position=$centerPosition")
            return
        }

        val videoList = viewModel.videoList.value
        if (videoList == null || centerPosition >= videoList.size) {
            Log.e(TAG, "attachPlayerToCenter: 视频列表为空或位置越界")
            return
        }

        val video = videoList[centerPosition]
        Log.d(TAG, "attachPlayerToCenter: 准备播放视频 - id=${video.id}, title=${video.title}")

        val videoContainer = viewHolder.itemView.findViewById<ViewGroup>(R.id.videoContainer)
        if (videoContainer == null) {
            Log.e(TAG, "attachPlayerToCenter: 未找到 videoContainer")
            return
        }

        // 获取封面ImageView
        val coverImageView = viewHolder.itemView.findViewById<ImageView>(R.id.ivCover)
        coverImageView?.visibility = View.VISIBLE  // 确保封面可见

        // 获取下一个视频（用于预加载）
        val nextPosition = centerPosition + 1
        val nextVideo = if (nextPosition < videoList.size) videoList[nextPosition] else null

        // 使用四播放器池播放视频
        playerPool.playVideo(
            container = videoContainer,
            videoUrl = video.videoUrl,
            position = centerPosition,
            nextVideoUrl = nextVideo?.videoUrl,
            nextPosition = if (nextVideo != null) nextPosition else -1,
            onFirstFrameRendered = object : VideoPlayerPool.OnFirstFrameRenderedListener {
                override fun onFirstFrameRendered() {
                    Log.d(TAG, "onFirstFrameRendered: 首帧渲染完成, position=$centerPosition")
                    // 🔥 视频开始播放时，立即隐藏封面
                    coverImageView?.let { cover ->
                        cover.visibility = View.GONE
                        Log.d(TAG, "onFirstFrameRendered: ✅ 封面已隐藏，视频开始播放")
                    }
                }
            }
        )

        Log.d(TAG, "attachPlayerToCenter: ========== 播放器挂载完成 ==========")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment 销毁视图")
        _binding = null
    }
}
