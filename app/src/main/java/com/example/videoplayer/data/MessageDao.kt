package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 消息数据访问对象
 *
 * 提供消息的增删改查操作
 */
@Dao
interface MessageDao {

    /**
     * 获取两个用户之间的所有消息
     *
     * 查询条件：(senderId=userId1 AND receiverId=userId2) OR (senderId=userId2 AND receiverId=userId1)
     * 这样可以获取双向的所有消息
     *
     * @param userId1 用户1的ID（通常是 "ME"）
     * @param userId2 用户2的ID（对方用户）
     * @return Flow 形式的消息列表，按时间戳升序排列
     */
    @Query("""
        SELECT * FROM messages
        WHERE (senderId = :userId1 AND receiverId = :userId2)
           OR (senderId = :userId2 AND receiverId = :userId1)
        ORDER BY timestamp ASC
    """)
    fun getMessages(userId1: String, userId2: String): Flow<List<MessageEntity>>

    /**
     * 插入一条新消息
     *
     * @param message 消息实体
     */
    @Insert
    suspend fun insertMessage(message: MessageEntity)

    /**
     * 获取所有消息（调试用）
     *
     * @return 所有消息列表
     */
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    /**
     * 删除所有消息（调试用）
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /**
     * 获取未读消息数量
     *
     * @param userId 用户ID
     * @return 未读消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE receiverId = :userId AND isRead = 0")
    suspend fun getUnreadCount(userId: String): Int

    /**
     * 将某个对话的所有消息标记为已读
     *
     * @param userId1 用户1的ID（通常是 "ME"）
     * @param userId2 用户2的ID（对方用户）
     */
    @Query("""
        UPDATE messages SET isRead = 1
        WHERE receiverId = :userId1 AND senderId = :userId2 AND isRead = 0
    """)
    suspend fun markAsRead(userId1: String, userId2: String)

    /**
     * 获取所有曾给当前用户发送过消息的用户ID列表
     *
     * 用途：自动将发送过消息的用户添加到联系人列表
     *
     * @param currentUserId 当前用户的ID
     * @return 发送过消息的用户ID列表（去重）
     */
    @Query("""
        SELECT DISTINCT senderId FROM messages
        WHERE receiverId = :currentUserId
        ORDER BY timestamp DESC
    """)
    suspend fun getMessageSenders(currentUserId: String): List<String>
}
