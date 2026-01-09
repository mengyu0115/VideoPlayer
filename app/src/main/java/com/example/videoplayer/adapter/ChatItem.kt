package com.example.videoplayer.adapter

import com.example.videoplayer.data.MessageEntity

/**
 * 消息列表项
 *
 * 支持两种类型：
 * - MessageItem: 实际消息
 * - TimestampItem: 时间戳分隔符
 */
//密封类：密封类中的所有子类都只能被定义在密封类内部
sealed class ChatItem {
    /**
     * 消息项
     */
    data class MessageItem(val message: MessageEntity) : ChatItem()

    /**
     * 时间戳分隔符
     */
    data class TimestampItem(val timestamp: Long) : ChatItem()
}
