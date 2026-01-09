package com.example.videoplayer.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.databinding.ItemVideoBinding

class VideoAdapter : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    companion object {
        private const val TAG = "VideoAdapter"
    }

    private val videoList = mutableListOf<VideoEntity>()

    // ========== 独立的互动状态管理（避免触发 RecyclerView 刷新）==========
    // 使用独立的 Map 存储互动状态，与 VideoEntity 更新完全隔离
    private val likeStateMap = mutableMapOf<String, Boolean>()
    private val likeCountMap = mutableMapOf<String, Int>()
    private val commentCountMap = mutableMapOf<String, Int>()
    private val favoriteStateMap = mutableMapOf<String, Boolean>()

    // 点击互动状态的回调
    var onLikeClick: ((videoId: String, isLiked: Boolean) -> Unit)? = null
    var onCommentClick: ((videoId: String) -> Unit)? = null
    var onFollowClick: ((userId: String, userName: String, avatarUrl: String) -> Unit)? = null
    var onFavoriteClick: ((videoId: String, isFavorite: Boolean) -> Unit)? = null
    var onCheckFollowStatus: ((userId: String, callback: (Boolean) -> Unit) -> Unit)? = null
    var onVideoClick: (() -> Unit)? = null

    init {
        Log.d(TAG, "init: VideoAdapter 创建")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        Log.d(TAG, "onCreateViewHolder: 创建新的 ViewHolder")
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val holder = VideoViewHolder(binding)
        Log.d(TAG, "onCreateViewHolder: ViewHolder 创建完成, hashCode=${holder.hashCode()}")
        return holder
    }
    // 绑定数据
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videoList[position]
        Log.d(TAG, "onBindViewHolder: 绑定位置 $position, 视频 id=${video.id}, title=${video.title}")
        holder.bind(video)
    }
    // 回收 ViewHolder
    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        Log.d(TAG, "onViewRecycled: ViewHolder 被回收, hashCode=${holder.hashCode()}")
    }
    // 获取列表大小
    override fun getItemCount(): Int {
        return videoList.size
    }

    // 更新列表
    fun submitList(videos: List<VideoEntity>) {
        Log.d(TAG, "submitList: 收到视频列表，数量: ${videos.size}")

        // 初始化或更新状态 Map
        videos.forEach { video ->
            if (!likeStateMap.containsKey(video.id)) {
                // 首次加载，初始化状态
                likeStateMap[video.id] = video.isLiked
                likeCountMap[video.id] = video.likeCount
                commentCountMap[video.id] = video.commentCount
                favoriteStateMap[video.id] = video.isFavorite
                Log.d(TAG, "submitList: 初始化视频 ${video.id} 的状态")
            } else {
                // 已有状态，从数据库更新（但不触发刷新）
                likeStateMap[video.id] = video.isLiked
                likeCountMap[video.id] = video.likeCount
                commentCountMap[video.id] = video.commentCount
                favoriteStateMap[video.id] = video.isFavorite
                Log.d(TAG, "submitList: 更新视频 ${video.id} 的状态（不触发刷新）")
            }
        }

        // 只在列表为空时才进行完整刷新
        if (videoList.isEmpty()) {
            videoList.addAll(videos)
            notifyDataSetChanged()
            Log.d(TAG, "submitList: 首次加载，使用 notifyDataSetChanged")
        } else {
            // 检测是否有新视频（通过 ID 比对）
            val oldVideoIds = videoList.map { it.id }.toSet()
            val newVideos = videos.filter { it.id !in oldVideoIds }

            if (newVideos.isNotEmpty()) {
                // 找出新视频并添加到列表开头（最新视频在前）
                videoList.addAll(0, newVideos)
                notifyItemRangeInserted(0, newVideos.size)
                Log.d(TAG, "submitList: 检测到 ${newVideos.size} 个新视频，添加到列表开头")
                newVideos.forEach { video ->
                    Log.d(TAG, "submitList:   新视频 - id=${video.id}, title=${video.title}")
                }
            } else if (videos.size != videoList.size) {
                // 视频数量变化但没有新ID（可能是删除），重建列表
                Log.w(TAG, "submitList: 视频数量变化但没有新视频（可能有删除），重建列表")
                videoList.clear()
                videoList.addAll(videos)
                notifyDataSetChanged()
            } else {
                // 后续只更新互动状态到 Map，不触发 RecyclerView 刷新
                Log.d(TAG, "submitList: 状态已静默更新到 Map，不会导致视频重播")
            }
        }
    }

    /**
     * 从数据库静默更新状态（不触发任何 RecyclerView 刷新）
     *
     * 这个方法被 Fragment 调用，用于在数据库更新后同步状态到 Map
     * 完全不调用 notify 方法，避免触发 onBindViewHolder
     */
    fun updateStatesFromDatabase(videos: List<VideoEntity>) {
        Log.d(TAG, "updateStatesFromDatabase: 从数据库静默更新状态，数量: ${videos.size}")

        videos.forEach { video ->
            // 静默更新 Map 中的状态
            likeStateMap[video.id] = video.isLiked
            likeCountMap[video.id] = video.likeCount
            commentCountMap[video.id] = video.commentCount
            favoriteStateMap[video.id] = video.isFavorite

            Log.v(TAG, "updateStatesFromDatabase: 更新 ${video.id} - like=${video.isLiked}, likeCount=${video.likeCount}")
        }

        Log.d(TAG, "updateStatesFromDatabase:  状态已静默更新，不触发任何刷新")
    }

    /**
     * 视频 ViewHolder
     */
    inner class VideoViewHolder(
        private val binding: ItemVideoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // 在 init 中设置监听器（只设置一次）
            setupClickListeners()
        }

        private fun setupClickListeners() {
            // 视频容器点击（暂停/播放）
            binding.videoContainer.setOnClickListener {
                Log.d(TAG, "视频容器点击: 切换播放/暂停状态")
                onVideoClick?.invoke()
            }

            // 点赞按钮
            binding.ivLike.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val video = videoList[position]
                val currentIsLiked = likeStateMap[video.id] ?: false
                val currentLikeCount = likeCountMap[video.id] ?: 0

                val newIsLiked = !currentIsLiked
                val newLikeCount = if (newIsLiked) currentLikeCount + 1 else currentLikeCount - 1

                Log.d(TAG, "点赞点击: videoId=${video.id}, newIsLiked=$newIsLiked, newLikeCount=$newLikeCount")

                // 立即更新 Map 和 UI（使用 isSelected 而不是 setColorFilter）
                likeStateMap[video.id] = newIsLiked
                likeCountMap[video.id] = newLikeCount
                binding.ivLike.isSelected = newIsLiked  //  切换点赞UI状态
                binding.tvLikeCount.text = formatCount(newLikeCount) // 更新点赞数

                // 通知 ViewModel 更新数据库
                onLikeClick?.invoke(video.id, newIsLiked)
            }

            // 评论按钮
            binding.ivComment.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val video = videoList[position]
                Log.d(TAG, "评论点击: videoId=${video.id}")
                onCommentClick?.invoke(video.id)
            }

            // 关注按钮
            binding.ivFollow.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val video = videoList[position]
                Log.d(TAG, "关注点击: authorId=${video.authorId}, authorName=${video.authorName}")

                // 立即隐藏关注按钮
                binding.ivFollow.visibility = android.view.View.GONE

                // 通知 ViewModel - 使用真实的authorId
                onFollowClick?.invoke(video.authorId, video.authorName, video.authorAvatarUrl)
            }

            // 收藏按钮
            binding.ivFavorite.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val video = videoList[position]
                val currentIsFavorite = favoriteStateMap[video.id] ?: false
                val newIsFavorite = !currentIsFavorite

                Log.d(TAG, "收藏点击: videoId=${video.id}, newIsFavorite=$newIsFavorite")

                // 立即更新 Map 和 UI（使用 isSelected 而不是 setColorFilter）
                favoriteStateMap[video.id] = newIsFavorite
                binding.ivFavorite.isSelected = newIsFavorite  //  只改变选中状态

                // 通知 ViewModel 更新数据库
                onFavoriteClick?.invoke(video.id, newIsFavorite)
            }
        }

        fun bind(video: VideoEntity) {
            Log.d(TAG, "ViewHolder.bind: 开始绑定视频 - id=${video.id}")

            // 🔥 加载封面图（确保尺寸与视频匹配）
            // 使用 centerCrop 确保封面填满整个容器，与视频播放时的显示效果一致
            binding.ivCover.visibility = android.view.View.VISIBLE
            binding.ivCover.load(video.coverUrl) {
                crossfade(false)  // 禁用淡入动画，立即显示
                memoryCacheKey(video.coverUrl)
                diskCacheKey(video.coverUrl)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
                networkCachePolicy(coil.request.CachePolicy.ENABLED)
            }

            // 加载用户头像
            binding.ivUserAvatar.load(video.authorAvatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }

            // 绑定文本信息
            binding.tvUserName.text = video.authorName
            binding.tvDescription.text = video.description

            // 从 Map 中获取互动数据（而不是从 video 对象）
            val isLiked = likeStateMap[video.id] ?: video.isLiked
            val likeCount = likeCountMap[video.id] ?: video.likeCount
            val commentCount = commentCountMap[video.id] ?: video.commentCount
            val isFavorite = favoriteStateMap[video.id] ?: video.isFavorite

            binding.tvLikeCount.text = formatCount(likeCount)
            binding.tvCommentCount.text = formatCount(commentCount)

            // 更新按钮状态（使用 isSelected 属性）
            binding.ivLike.isSelected = isLiked
            binding.ivFavorite.isSelected = isFavorite

            // 检查关注状态
            val userId = "user_${video.id}"
            onCheckFollowStatus?.invoke(userId) { isFollowing ->
                binding.ivFollow.visibility = if (isFollowing) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
            }

            Log.d(TAG, "ViewHolder.bind: 完成 - 点赞=$likeCount 评论=$commentCount")
        }

        private fun formatCount(count: Int): String {
            return when {
                count >= 10000 -> String.format("%.1fw", count / 10000.0)
                count >= 1000 -> String.format("%.1fk", count / 1000.0)
                else -> count.toString()
            }
        }
    }
}
