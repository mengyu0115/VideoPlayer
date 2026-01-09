package com.example.videoplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.databinding.ItemProfileButtonsBinding
import com.example.videoplayer.databinding.ItemProfileVideoBinding

/**已弃用
 * ProfileVideoAdapter - 个人中心视频列表适配器
 *
 * 支持多ViewType：
 * - ViewType 0: 按钮布局（我的视频/我的收藏）
 * - ViewType 1: 视频卡片
 */


class ProfileVideoAdapter(
    private val onVideoClick: (VideoEntity) -> Unit,
    private val onMyVideosClick: () -> Unit,
    private val onMyFavoritesClick: () -> Unit
) : ListAdapter<ProfileItem, RecyclerView.ViewHolder>(ProfileItemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_BUTTONS = 0
        private const val VIEW_TYPE_VIDEO = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ProfileItem.ButtonsItem -> VIEW_TYPE_BUTTONS
            is ProfileItem.VideoItem -> VIEW_TYPE_VIDEO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_BUTTONS -> {
                val binding = ItemProfileButtonsBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ButtonsViewHolder(binding, onMyVideosClick, onMyFavoritesClick)
            }
            VIEW_TYPE_VIDEO -> {
                val binding = ItemProfileVideoBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                VideoViewHolder(binding, onVideoClick)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ProfileItem.ButtonsItem -> {
                (holder as ButtonsViewHolder).bind(item)
                // 设置按钮占满全宽（跨越所有列）
                val layoutParams = holder.itemView.layoutParams
                if (layoutParams is androidx.recyclerview.widget.StaggeredGridLayoutManager.LayoutParams) {
                    layoutParams.isFullSpan = true
                }
            }
            is ProfileItem.VideoItem -> (holder as VideoViewHolder).bind(item.video)
        }
    }

    // 按钮ViewHolder
    class ButtonsViewHolder(
        private val binding: ItemProfileButtonsBinding,
        private val onMyVideosClick: () -> Unit,
        private val onMyFavoritesClick: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProfileItem.ButtonsItem) {
            // 更新按钮状态
            if (item.selectedMode == 0) { // 我的视频
                binding.btnMyVideos.setBackgroundColor(0xFF1E88E5.toInt())
                binding.btnMyVideos.setTextColor(0xFFFFFFFF.toInt())
                binding.btnMyFavorites.setBackgroundColor(0xFFE0E0E0.toInt())
                binding.btnMyFavorites.setTextColor(0xFF666666.toInt())
            } else { // 我的收藏
                binding.btnMyVideos.setBackgroundColor(0xFFE0E0E0.toInt())
                binding.btnMyVideos.setTextColor(0xFF666666.toInt())
                binding.btnMyFavorites.setBackgroundColor(0xFF1E88E5.toInt())
                binding.btnMyFavorites.setTextColor(0xFFFFFFFF.toInt())
            }

            // 设置点击事件
            binding.btnMyVideos.setOnClickListener {
                onMyVideosClick()
            }
            binding.btnMyFavorites.setOnClickListener {
                onMyFavoritesClick()
            }
        }
    }

    // 视频ViewHolder
    class VideoViewHolder(
        private val binding: ItemProfileVideoBinding,
        private val onVideoClick: (VideoEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoEntity) {
            // 加载封面
            binding.ivCover.load(video.coverUrl) {
                crossfade(true)
                error(android.R.drawable.ic_menu_gallery)
                placeholder(android.R.drawable.ic_menu_gallery)
            }

            // 设置标题
            binding.tvTitle.text = video.title

            // 点击事件
            binding.root.setOnClickListener {
                onVideoClick(video)
            }
        }
    }

    private class ProfileItemDiffCallback : DiffUtil.ItemCallback<ProfileItem>() {
        override fun areItemsTheSame(oldItem: ProfileItem, newItem: ProfileItem): Boolean {
            return when {
                oldItem is ProfileItem.ButtonsItem && newItem is ProfileItem.ButtonsItem -> true
                oldItem is ProfileItem.VideoItem && newItem is ProfileItem.VideoItem ->
                    oldItem.video.id == newItem.video.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ProfileItem, newItem: ProfileItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * ProfileItem - 个人中心列表项封装类
 */
sealed class ProfileItem {
    data class ButtonsItem(val selectedMode: Int) : ProfileItem() // 0=我的视频, 1=我的收藏
    data class VideoItem(val video: VideoEntity) : ProfileItem()
}
