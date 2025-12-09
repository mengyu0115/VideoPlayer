package com.example.videoplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videoplayer.databinding.ItemEmojiBinding

/**
 * 表情适配器
 *
 * 显示常用表情列表
 */
class EmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val binding = ItemEmojiBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EmojiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.bind(emojis[position])
    }

    override fun getItemCount(): Int = emojis.size

    inner class EmojiViewHolder(
        private val binding: ItemEmojiBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(emoji: String) {
            binding.tvEmoji.text = emoji
            binding.tvEmoji.setOnClickListener {
                onEmojiClick(emoji)
            }
        }
    }
}
