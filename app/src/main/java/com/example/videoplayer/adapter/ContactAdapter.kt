package com.example.videoplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.data.UserEntity
import com.example.videoplayer.databinding.ItemContactBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 联系人列表适配器
 * 联系人列表更新时，会自动更新列表
 * 展示关注的用户列表，支持点击跳转到聊天页面
 */
class ContactAdapter : ListAdapter<UserEntity, ContactAdapter.ContactViewHolder>(ContactDiffCallback()) {

    // 列表项点击事件
    var onItemClick: ((UserEntity) -> Unit)? = null

    //覆写 onCreateViewHolder 方法，用于创建 ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        //创建 ItemContactBinding，得到指向联系人xml视图的binding，LayoutInflater.from(parent.context)指定了布局的父视图
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * 联系人列表项 ViewHolder
     *
     * @param binding 绑定项
     */
    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: UserEntity) {
            // 加载头像
            binding.ivAvatar.load(user.avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(android.R.drawable.ic_menu_report_image)
                placeholder(android.R.drawable.ic_menu_report_image)
            }

            // 用户名
            binding.tvUserName.text = user.userName

            // 关注时间（格式化）
            binding.tvTime.text = formatFollowTime(user.followedAt)

            // 点击事件
            binding.root.setOnClickListener {
                //invoke 点击回调
                onItemClick?.invoke(user)
            }
        }

        /**
         * 格式化关注时间
         *
         * @param timestamp 时间戳
         * @return 格式化后的时间字符串（如"刚刚"、"2天前"、"2023-12-01"）
         */
        private fun formatFollowTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    /**
     * DiffUtil 回调，用于高效更新列表
     */
    private class ContactDiffCallback : DiffUtil.ItemCallback<UserEntity>() {
        //判断有无新数据
        override fun areItemsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: UserEntity, newItem: UserEntity): Boolean {
            return oldItem == newItem
        }
    }
}
