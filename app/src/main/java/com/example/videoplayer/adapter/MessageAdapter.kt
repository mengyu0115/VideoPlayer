package com.example.videoplayer.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videoplayer.data.MessageEntity
import com.example.videoplayer.databinding.ItemMessageReceivedBinding
import com.example.videoplayer.databinding.ItemMessageSentBinding
import com.example.videoplayer.databinding.ItemTimestampBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 消息列表适配器
 *
 * 支持三种视图类型：
 * - TYPE_SENT: 我发送的消息（右对齐）
 * - TYPE_RECEIVED: 对方发送的消息（左对齐）
 * - TYPE_TIMESTAMP: 时间戳分隔符（居中显示）
 *
 * 每隔 5 分钟自动插入时间戳分隔符
 */
class MessageAdapter(private val myUserId: String = "ME") :
    //ListAdapter：用于高效更新列表，只更新变化的部分
    ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback()) {

    companion object {
        private const val TAG = "MessageAdapter"
        private const val TYPE_SENT = 1       // 我发送的消息
        private const val TYPE_RECEIVED = 2   // 对方发送的消息
        private const val TYPE_TIMESTAMP = 3  // 时间戳分隔符

        private const val TIME_GAP_MS = 5 * 60 * 1000L  // 5 分钟

        /**
         * 格式化时间戳为 HH:mm 格式
         */
        fun formatTime(timestamp: Long): String {
            val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            Log.d(TAG, "formatTime: timestamp=$timestamp (${Date(timestamp)}) -> $formatted")
            return formatted
        }

        /**
         * 格式化时间戳为完整日期时间
         */
        fun formatTimestamp(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val today = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val yesterday = today - 24 * 60 * 60 * 1000L

            Log.d(TAG, "formatTimestamp: timestamp=$timestamp, now=$now, today=$today")
            Log.d(TAG, "formatTimestamp: timestamp date=${Date(timestamp)}, now date=${Date(now)}")

            return when {
                timestamp >= today -> {
                    // 今天：显示 "今天 HH:mm"
                    "今天 ${formatTime(timestamp)}"
                }
                timestamp >= yesterday -> {
                    // 昨天：显示 "昨天 HH:mm"
                    "昨天 ${formatTime(timestamp)}"
                }
                else -> {
                    // 更早：显示 "MM-dd HH:mm"
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
                }
            }
        }

        /**
         * 将消息列表转换为带时间戳的列表
         */
        fun convertToItems(messages: List<MessageEntity>): List<ChatItem> {
            Log.d(TAG, "━━━━━━━━━━ convertToItems 开始 ━━━━━━━━━━")
            Log.d(TAG, "convertToItems: 输入消息数量 = ${messages.size}")

            if (messages.isEmpty()) {
                Log.d(TAG, "convertToItems: 消息列表为空，返回空列表")
                return emptyList()
            }

            //mutableListOf：创建一个可变列表
            //创建空列表，用于存放转换后的消息列表
            val items = mutableListOf<ChatItem>()
            var lastTimestamp = 0L

            messages.forEachIndexed { index, message ->
                Log.d(TAG, "convertToItems: [$index] 处理消息 - id=${message.id}, senderId=${message.senderId}, content=${message.content}, timestamp=${message.timestamp}")

                // 如果距离上次时间戳超过 5 分钟，插入新的时间戳
                if (message.timestamp - lastTimestamp >= TIME_GAP_MS) {
                    items.add(ChatItem.TimestampItem(message.timestamp))
                    lastTimestamp = message.timestamp
                    Log.d(TAG, "convertToItems: 插入时间戳分隔符")
                }

                items.add(ChatItem.MessageItem(message))
                Log.d(TAG, "convertToItems: 添加消息到items")
            }

            Log.d(TAG, "convertToItems: 输出items数量 = ${items.size}")
            Log.d(TAG, "━━━━━━━━━━ convertToItems 结束 ━━━━━━━━━━")
            return items
        }
    }

    //重写getItemViewType方法，根据消息类型返回不同的视图类型
    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.MessageItem -> {
                if (item.message.senderId == myUserId) TYPE_SENT else TYPE_RECEIVED
            }
            is ChatItem.TimestampItem -> TYPE_TIMESTAMP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            //我发送的消息
            TYPE_SENT -> {
                //创建ItemMessageSentBinding，得到指向我的消息xml视图的binding，LayoutInflater.from(parent.context)指定了布局的父视图
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                //将binding传入SentMessageViewHolder，
                SentMessageViewHolder(binding)
            }
            TYPE_RECEIVED -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedMessageViewHolder(binding)
            }
            TYPE_TIMESTAMP -> {
                val binding = ItemTimestampBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TimestampViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    /**
     * 我发送的消息 ViewHolder
     */
    class SentMessageViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        //绑定消息数据到视图上
        fun bind(message: MessageEntity) {
            binding.tvMessage.text = message.content
            binding.tvTime.text = formatTime(message.timestamp)
        }
    }

    /**
     * 对方发送的消息 ViewHolder
     */
    class ReceivedMessageViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: MessageEntity) {
            binding.tvMessage.text = message.content
            binding.tvTime.text = formatTime(message.timestamp)
        }
    }

    /**
     * 时间戳分隔符 ViewHolder
     */
    class TimestampViewHolder(
        private val binding: ItemTimestampBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(timestamp: Long) {
            binding.tvTimestamp.text = formatTimestamp(timestamp)
        }
    }

    //重写onBindViewHolder方法，根据消息类型绑定数据到视图上
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            //属于发送的消息
            is ChatItem.MessageItem -> {
                //根据消息类型绑定数据到视图上
                // Kotlin的智能转换，holder 从 RecyclerView.ViewHolder 转换为 SentMessageViewHolder 所以可以调用 SentMessageViewHolder 的方法
                when (holder) {
                    is SentMessageViewHolder -> holder.bind(item.message)
                    is ReceivedMessageViewHolder -> holder.bind(item.message)
                }
            }
            //属于时间戳
            is ChatItem.TimestampItem -> {
                (holder as TimestampViewHolder).bind(item.timestamp)
            }
        }
    }


    /**
     * DiffUtil 回调，用于高效更新列表
     */
    private class ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        //判断两个项目是否是相同的项
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return when {
                oldItem is ChatItem.MessageItem && newItem is ChatItem.MessageItem -> {
                    oldItem.message.id == newItem.message.id
                }
                oldItem is ChatItem.TimestampItem && newItem is ChatItem.TimestampItem -> {
                    oldItem.timestamp == newItem.timestamp
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }
}
