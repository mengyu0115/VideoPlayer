package com.example.videoplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.databinding.ItemGridVideoBinding

/**
 * GridVideoAdapter - 网格视频适配器
 *
 * 只展示视频封面，用于三列瀑布流展示
 */
class GridVideoAdapter(
    private val onVideoClick: (VideoEntity) -> Unit
) : ListAdapter<VideoEntity, GridVideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemGridVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding, onVideoClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        private val binding: ItemGridVideoBinding,
        private val onVideoClick: (VideoEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(video: VideoEntity) {
            // 加载封面
            binding.ivCover.load(video.coverUrl) {
                crossfade(true)
                error(android.R.drawable.ic_menu_gallery)
                placeholder(android.R.drawable.ic_menu_gallery)
                memoryCachePolicy(CachePolicy.ENABLED)  // 内存缓存
                diskCachePolicy(CachePolicy.ENABLED)    // 磁盘缓存
            }

            // 点击事件
            binding.root.setOnClickListener {
                onVideoClick(video)
            }
        }
    }

    /**
     * 视频列表DiffCallback
     */
    private class VideoDiffCallback : DiffUtil.ItemCallback<VideoEntity>() {
        // 判断两个视频是否相同
        override fun areItemsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
            return oldItem.id == newItem.id
        }
        // 判断两个视频内容是否相同
        override fun areContentsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
            return oldItem == newItem
        }
    }
}
