package com.example.videoplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.data.CommentEntity
import com.example.videoplayer.databinding.ItemCommentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 评论列表适配器
 *
 * 用于展示视频的评论列表
 */
class CommentAdapter : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {
    // 评论列表
    private val comments = mutableListOf<CommentEntity>()

    /**
     * 创建评论视图持有者
     *
     * @param parent 父布局
     * @param viewType 视图类型
     * @return 创建的评论视图持有者
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    /**
     * 提交新的评论列表
     *
     * @param newComments 新的评论列表
     */
    fun submitList(newComments: List<CommentEntity>) {
        comments.clear()
        comments.addAll(newComments)
        // 通知数据集已发生更改
        notifyDataSetChanged()
    }
    /**
     * 评论视图持有者
     *
     * @param binding 绑定项
     */
    inner class CommentViewHolder(
        private val binding: ItemCommentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(comment: CommentEntity) {
            // 绑定用户昵称
            binding.tvUserName.text = comment.userName

            // 绑定评论内容
            binding.tvContent.text = comment.content

            // 绑定评论时间（格式化）
            binding.tvTime.text = formatTime(comment.timestamp)

            // 加载用户头像（圆形裁剪）
            binding.ivAvatar.load(comment.avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        }

        /**
         * 格式化时间戳
         *
         * 规则：
         * - 1分钟内：刚刚
         * - 1小时内：X分钟前
         * - 24小时内：X小时前
         * - 7天内：X天前
         * - 其他：显示日期（MM-dd HH:mm）
         *
         * @param timestamp 时间戳（毫秒）
         * @return 格式化后的时间字符串
         */
        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
                diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
                else -> {
                    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
}
