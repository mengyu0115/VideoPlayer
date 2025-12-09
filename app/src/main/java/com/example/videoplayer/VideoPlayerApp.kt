package com.example.videoplayer

import android.app.Application
import android.util.Log
import com.example.videoplayer.data.AppDatabase
import com.example.videoplayer.network.SocketManager
import com.example.videoplayer.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
     * 全局消息监听器（迭代15）
     *
     * 解决问题：
     * - ChatViewModel 只在 ChatActivity 中存在，用户没有打开聊天页面时消息不会被保存
     * - 现在在应用级别监听 SocketManager.messageFlow
     * - 收到消息后自动保存到数据库并添加联系人
     * - 保证即使用户不在聊天页面也能接收消息
     */
    private fun startGlobalMessageListener() {
        Log.d(TAG, "startGlobalMessageListener: 启动全局消息监听器")

        applicationScope.launch {
            try {
                SocketManager.messageFlow.collect { message ->
                    Log.d(TAG, "收到新消息: ${message.senderId} -> ${message.receiverId}: ${message.content}")

                    // 保存到数据库
                    database.messageDao().insertMessage(message)
                    Log.d(TAG, "消息已保存到数据库")

                    // 自动添加发送者到联系人列表
                    val repository = VideoRepository(this@VideoPlayerApp)
                    val addedCount = repository.autoAddMessageSendersToContacts()
                    if (addedCount > 0) {
                        Log.d(TAG, " 已自动添加 $addedCount 个新联系人")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "全局消息监听器异常", e)
            }
        }
    }
}
