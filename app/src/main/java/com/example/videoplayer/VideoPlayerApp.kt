package com.example.videoplayer

import android.app.Application
import android.util.Log
import com.example.videoplayer.data.AppDatabase
import com.example.videoplayer.network.SocketManager
import com.example.videoplayer.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用程序入口
 *
 * 用于初始化全局单例资源（数据库等）
 * 迭代15新增：全局消息监听器，解决实时消息接收问题
 */
class VideoPlayerApp : Application() {

    companion object {
        private const val TAG = "VideoPlayerApp"

        lateinit var instance: VideoPlayerApp
            private set

        lateinit var database: AppDatabase
            private set
    }

    //  应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // 初始化全局实例
        instance = this

        // 初始化数据库
        database = AppDatabase.getInstance(this)

        //  启动全局消息监听器
        startGlobalMessageListener()
    }

    /**
     * 全局消息监听器（迭代15 + Bug修复）
     *
     * 解决问题：
     * - ChatViewModel 只在 ChatActivity 中存在，用户没有打开聊天页面时消息不会被保存
     * - 现在在应用级别监听 SocketManager.messageFlow
     * - 收到消息后自动保存到数据库并添加联系人
     * - 保证即使用户不在聊天页面也能接收消息
     *
     * Bug修复：
     * - 过滤自己发送的消息（避免服务器回显导致重复插入）
     * - 因为ChatViewModel.sendMessage已经插入了自己发送的消息
     * - 添加详细日志追踪消息重复问题
     */
    private fun startGlobalMessageListener() {
        Log.d(TAG, "startGlobalMessageListener: 启动全局消息监听器")

        applicationScope.launch {
            try {
                SocketManager.messageFlow.collect { message ->
                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d(TAG, "收到新消息: ${message.senderId} -> ${message.receiverId}: ${message.content}")
                    Log.d(TAG, "消息ID: ${message.id}, 时间戳: ${message.timestamp}")

                    //  关键修复：过滤自己发送的消息
                    // 获取当前用户ID
                    val currentUserId = com.example.videoplayer.utils.SessionManager
                        .getInstance(this@VideoPlayerApp)
                        .currentUserId

                    Log.d(TAG, "当前用户ID: $currentUserId")

                    // 如果是自己发送的消息（senderId == currentUserId），则不插入
                    // 因为 ChatViewModel.sendMessage 已经插入过了
                    if (message.senderId == currentUserId) {
                        Log.d(TAG, "⚠ 过滤自己发送的消息（已由ChatViewModel插入），跳过")
                        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        return@collect
                    }

                    // 检查数据库是否已存在相同消息（防止重复插入）
                    // 使用 first() 获取当前数据库中的消息列表
                    try {
                        val existingMessages = database.messageDao().getAllMessages().first()
                        val isDuplicate = existingMessages.any {
                            it.senderId == message.senderId &&
                            it.receiverId == message.receiverId &&
                            it.content == message.content &&
                            kotlin.math.abs(it.timestamp - message.timestamp) < 1000  // 1秒内的消息视为重复
                        }

                        if (isDuplicate) {
                            Log.w(TAG, " 检测到重复消息，跳过插入")
                            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            return@collect
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "检测重复消息时出错: ${e.message}")
                    }

                    // 只保存别人发送给我的消息
                    Log.d(TAG, " 准备插入消息到数据库...")
                    database.messageDao().insertMessage(message)
                    Log.d(TAG, " 消息已保存到数据库")

                    // 自动添加发送者到联系人列表
                    val repository = VideoRepository(this@VideoPlayerApp)
                    val addedCount = repository.autoAddMessageSendersToContacts()
                    if (addedCount > 0) {
                        Log.d(TAG, " 已自动添加 $addedCount 个新联系人")
                    }

                    Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                }
            } catch (e: Exception) {
                Log.e(TAG, "全局消息监听器异常", e)
            }
        }
    }
}
