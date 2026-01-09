package com.example.videoplayer.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.VideoPlayerApp
import com.example.videoplayer.data.MessageEntity
import com.example.videoplayer.network.SocketManager
import com.example.videoplayer.repository.VideoRepository
import com.example.videoplayer.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * 聊天ViewModel（迭代14重构：使用 SocketManager）
 *
 * 管理聊天消息的发送和接收
 * 通过 SocketManager 实现健壮的 Socket 通信
 *
 * 核心变更：
 * - 使用 SocketManager 替代 ChatClient
 * - 监听 SocketManager.messageFlow 并写入 Room
 * - 自动添加消息发送者到联系人列表
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val messageDao = VideoPlayerApp.database.messageDao()
    private val sessionManager = SessionManager.getInstance(application)
    private val repository = VideoRepository(application)  //  添加 Repository

    // ❌ 移除重复监听器！
    // VideoPlayerApp 已经全局监听 messageFlow 并自动保存消息
    // 这里不需要再次监听，否则会导致消息重复插入（三连发送bug）

    // init {
    //     viewModelScope.launch {
    //         SocketManager.messageFlow.collect { message ->
    //             messageDao.insertMessage(message)
    //             autoAddSenderToContacts(message.senderId)
    //         }
    //     }
    // }

    /**
     * 获取与指定用户的所有消息（迭代13修复：多用户数据隔离）
     *
     * 架构变更：使用真实的当前用户ID，而不是硬编码 "ME"
     *
     * @param targetUserId 对方用户 ID
     * @return LiveData<List<MessageEntity>> 消息列表（按时间升序）
     */
    fun getMessages(targetUserId: String): LiveData<List<MessageEntity>> {
        val currentUserId = sessionManager.currentUserId  //  使用真实用户ID
        Log.d(TAG, "getMessages: currentUserId=$currentUserId, targetUserId=$targetUserId")
        return messageDao.getMessages(currentUserId, targetUserId).asLiveData()
    }

    /**
     * 发送消息（迭代14重构：使用 SocketManager）
     *
     * @param targetUserId 对方用户 ID
     * @param content 消息内容
     */
    fun sendMessage(targetUserId: String, content: String) {
        viewModelScope.launch {
            try {
                val currentUserId = sessionManager.currentUserId
                val timestamp = System.currentTimeMillis()

                // 先插入到数据库（我发送的消息）
                val message = MessageEntity(
                    senderId = currentUserId,
                    receiverId = targetUserId,
                    content = content,
                    timestamp = timestamp,
                    isRead = true  // 我发送的消息默认已读
                )
                messageDao.insertMessage(message)
                Log.d(TAG, "sendMessage:  消息已保存到数据库 (from=$currentUserId to=$targetUserId)")
                Log.d(TAG, "sendMessage: timestamp=$timestamp (${java.util.Date(timestamp)})")

                //  通过 SocketManager 发送给对方
                SocketManager.send(targetUserId, content)
                Log.d(TAG, "sendMessage: 消息已通过 SocketManager 发送")

            } catch (e: Exception) {
                Log.e(TAG, "sendMessage:  发送失败", e)
            }
        }
    }

    /**
     * 将对话标记为已读（迭代13修复：多用户数据隔离）
     *
     * 架构变更：使用真实的当前用户ID
     *
     * @param targetUserId 对方用户 ID
     */
    fun markAsRead(targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = sessionManager.currentUserId  //  使用真实用户ID
                messageDao.markAsRead(currentUserId, targetUserId)
                Log.d(TAG, "markAsRead:  对话已标记为已读 (currentUser=$currentUserId, target=$targetUserId)")
            } catch (e: Exception) {
                Log.e(TAG, "markAsRead:  标记失败", e)
            }
        }
    }

    /**
     * 获取未读消息数量
     *
     * @return 未读消息数量
     */
    suspend fun getUnreadCount(): Int {
        return try {
            val currentUserId = sessionManager.currentUserId
            messageDao.getUnreadCount(currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "getUnreadCount:  获取失败", e)
            0
        }
    }

    // ❌ 移除此方法，因为已由 VideoPlayerApp 全局处理
    // private fun autoAddSenderToContacts(senderId: String) { ... }
}
