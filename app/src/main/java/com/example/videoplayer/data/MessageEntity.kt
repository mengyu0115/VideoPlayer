package com.example.videoplayer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息数据库实体
 *
 * 用于存储聊天消息记录
 * 支持点对点聊天，固定当前用户 ID 为 "ME"
 *
 * 字段说明：
 * @param id 消息 ID（主键，自增）
 * @param senderId 发送者 ID（"ME" 表示当前用户）
 * @param receiverId 接收者 ID
 * @param content 消息内容
 * @param timestamp 发送时间戳
 * @param isRead 是否已读（默认 false）
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["senderId", "receiverId"]), // 发送者和接收者ID联合索引
        Index(value = ["timestamp"]),               // 按时间排序优化
        Index(value = ["isRead"])                   // 查询未读消息优化
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderId: String,        // "ME" 或对方用户 ID
    val receiverId: String,      // 对方用户 ID 或 "ME"
    val content: String,         // 消息内容
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false  // 是否已读
)
