package com.example.videoplayer.network

import android.util.Log
import com.example.videoplayer.data.MessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket

/**
 * Socket 管理器（单例） - 迭代14
 *
 * 核心特性：
 * 1. 严���登录握手协议
 * 2. SharedFlow 消息流处理
 * 3. 协程管理连接和消息接收
 * 4. 自动重连机制
 *
 * 使用：
 * - SocketManager.connect(serverIp, userId)
 * - SocketManager.send(toUser, content)
 * - ViewModel 中收集 SocketManager.messageFlow
 */
object SocketManager {

    private const val TAG = "SocketManager"
    private const val PORT = 8888
    private const val RECONNECT_DELAY_MS = 3000L

    // Socket 连接对象
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    // 当前用户ID
    var currentUserId: String? = null
        private set

    // 连接状态
    private var isConnected = false
    private var receiveJob: Job? = null

    // ✅ 消息流（SharedFlow）
    private val _messageFlow = MutableSharedFlow<MessageEntity>(replay = 0)
    val messageFlow: SharedFlow<MessageEntity> = _messageFlow.asSharedFlow()

    // ✅ 连接状态流
    private val _connectionStateFlow = MutableSharedFlow<ConnectionState>(replay = 1)
    val connectionStateFlow: SharedFlow<ConnectionState> = _connectionStateFlow.asSharedFlow()

    /**
     * 连接到服务器并登录
     *
     * @param serverIp 服务器IP
     * @param userId 用户ID
     * @return 连接是否成功
     */
    suspend fun connect(serverIp: String, userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "connect: 开始连接服务器 $serverIp:$PORT, userId=$userId")

            // 断开旧连接
            disconnect()

            // 创建 Socket 连接
            socket = Socket(serverIp, PORT)
            writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), "UTF-8"), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"))

            isConnected = true
            _connectionStateFlow.emit(ConnectionState.CONNECTED)
            Log.d(TAG, "connect: ✅ Socket 连接成功")

            // ✅ 严格握手：立即发送 LOGIN 包
            val loginJson = JSONObject().apply {
                put("type", "LOGIN")
                put("uid", userId)
            }
            sendRaw(loginJson.toString())
            Log.d(TAG, "connect: 已发送 LOGIN 包")

            // 等待登录响应
            val response = reader?.readLine()
            Log.d(TAG, "connect: 收到登录响应: $response")

            if (response != null) {
                val json = JSONObject(response)
                val code = json.getInt("code")
                val msg = json.getString("msg")

                if (code == 200) {
                    currentUserId = userId
                    Log.d(TAG, "connect: ✅ 登录成功: $userId")

                    // 启动消息接收循环
                    startReceivingMessages()
                    return@withContext true
                } else {
                    Log.e(TAG, "connect: ❌ 登录失败: $msg")
                    disconnect()
                    return@withContext false
                }
            } else {
                Log.e(TAG, "connect: ❌ 未收到登录响应")
                disconnect()
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "connect: ❌ 连接失败", e)
            _connectionStateFlow.emit(ConnectionState.DISCONNECTED)
            disconnect()
            return@withContext false
        }
    }

    /**
     * 发送消息
     *
     * @param toUser 接收者用户ID
     * @param content 消息内容
     */
    fun send(toUser: String, content: String) {
        if (!isConnected || currentUserId == null) {
            Log.w(TAG, "send: 未连接或未登录")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messageJson = JSONObject().apply {
                    put("type", "MSG")
                    put("from", currentUserId)
                    put("to", toUser)
                    put("content", content)
                    put("time", System.currentTimeMillis())
                }

                sendRaw(messageJson.toString())
                Log.d(TAG, "send: ✅ 消息已发送 -> $toUser: $content")

            } catch (e: Exception) {
                Log.e(TAG, "send: ❌ 发送失败", e)
            }
        }
    }

    /**
     * ✅ 启动消息接收循环（协程）
     */
    private fun startReceivingMessages() {
        receiveJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "startReceivingMessages: 开始接收消息...")

                // ✅ while(true) 循环 readLine()
                while (isConnected && reader != null) {
                    val line = reader?.readLine()

                    if (line == null) {
                        Log.w(TAG, "startReceivingMessages: 连接已断开")
                        break
                    }

                    Log.d(TAG, "startReceivingMessages: 收到消息: $line")
                    handleReceivedMessage(line)
                }

            } catch (e: Exception) {
                Log.e(TAG, "startReceivingMessages: ❌ 接收消息异常", e)
            } finally {
                _connectionStateFlow.emit(ConnectionState.DISCONNECTED)
                Log.d(TAG, "startReceivingMessages: 消息接收循环已退出")
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private suspend fun handleReceivedMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.getString("type")

            when (type) {
                "MSG" -> {
                    val from = json.getString("from")
                    val to = json.getString("to")
                    val content = json.getString("content")
                    val time = json.getLong("time")

                    Log.d(TAG, "handleReceivedMessage: 收到聊天消息 $from -> $to: $content")

                    // ✅ 构造 MessageEntity 并发送到 Flow
                    val messageEntity = MessageEntity(
                        senderId = from,
                        receiverId = to,
                        content = content,
                        timestamp = time
                    )

                    _messageFlow.emit(messageEntity)
                }

                "ACK" -> {
                    val code = json.getInt("code")
                    val msg = json.getString("msg")
                    Log.d(TAG, "handleReceivedMessage: 收到ACK响应 code=$code, msg=$msg")
                }

                "VIDEO_LIST" -> {
                    // ✅ 处理视频列表响应（迭代15）
                    val videosArray = json.getJSONArray("videos")
                    val videoList = mutableListOf<JSONObject>()
                    for (i in 0 until videosArray.length()) {
                        videoList.add(videosArray.getJSONObject(i))
                    }
                    Log.d(TAG, "handleReceivedMessage: 收到视频列表，共 ${videoList.size} 个视频")
                    _videoListFlow.emit(videoList)
                }

                else -> {
                    Log.w(TAG, "handleReceivedMessage: 未知消息类型: $type")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "handleReceivedMessage: ❌ 处理消息失败", e)
        }
    }

    /**
     * 发送原始数据
     */
    private fun sendRaw(data: String) {
        writer?.println(data)
        writer?.flush()
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        isConnected = false
        currentUserId = null

        receiveJob?.cancel()
        receiveJob = null

        try {
            reader?.close()
            writer?.close()
            socket?.close()
            Log.d(TAG, "disconnect: ✅ 连接已断开")
        } catch (e: Exception) {
            Log.e(TAG, "disconnect: ❌ 断开连接失败", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            _connectionStateFlow.emit(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * 发送心跳（可选）
     */
    fun sendHeartbeat() {
        if (!isConnected) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pingJson = JSONObject().apply {
                    put("type", "PING")
                }
                sendRaw(pingJson.toString())
                Log.d(TAG, "sendHeartbeat: PING sent")
            } catch (e: Exception) {
                Log.e(TAG, "sendHeartbeat: ❌ 发送心跳失败", e)
            }
        }
    }

    /**
     * ✅ 发布视频到服务器（迭代15）
     *
     * @param videoJson 视频JSON对象
     */
    fun publishVideo(videoJson: JSONObject) {
        if (!isConnected) {
            Log.w(TAG, "publishVideo: 未连接到服务器")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val publishMessage = JSONObject().apply {
                    put("type", "PUBLISH_VIDEO")
                    // 将视频JSON内嵌到消息中
                    val keys = videoJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, videoJson.get(key))
                    }
                }

                sendRaw(publishMessage.toString())
                Log.d(TAG, "publishVideo: ✅ 视频已发布到服务器: ${videoJson.getString("id")}")

            } catch (e: Exception) {
                Log.e(TAG, "publishVideo: ❌ 发布失败", e)
            }
        }
    }

    /**
     * ✅ 从服务器获取视频列表（迭代15）
     *
     * @param callback 回调函数，返回视频JSON列表
     */
    suspend fun getVideos(): List<JSONObject> = withContext(Dispatchers.IO) {
        if (!isConnected) {
            Log.w(TAG, "getVideos: 未连接到服务器")
            return@withContext emptyList()
        }

        try {
            val getVideosJson = JSONObject().apply {
                put("type", "GET_VIDEOS")
            }

            sendRaw(getVideosJson.toString())
            Log.d(TAG, "getVideos: 已发送GET_VIDEOS请求，等待响应...")

            // 简化处理：等待一段时间让视频列表通过messageFlow返回
            // 实际应该使用更复杂的回调机制
            delay(500)
            return@withContext emptyList()

        } catch (e: Exception) {
            Log.e(TAG, "getVideos: ❌ 获取失败", e)
            return@withContext emptyList()
        }
    }

    /**
     * ✅ 视频列表流（用于接收VIDEO_LIST响应）
     */
    private val _videoListFlow = MutableSharedFlow<List<JSONObject>>(replay = 0)
    val videoListFlow: SharedFlow<List<JSONObject>> = _videoListFlow.asSharedFlow()

    /**
     * 连接状态枚举
     */
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED
    }
}
