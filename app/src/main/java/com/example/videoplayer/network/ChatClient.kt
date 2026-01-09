package com.example.videoplayer.network

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.videoplayer.VideoPlayerApp
import com.example.videoplayer.data.MessageEntity
import com.example.videoplayer.repository.VideoRepository
import com.example.videoplayer.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket

/**
 * Socket 聊天客户端（单例）
 *
 * 功能：
 * 1. 管理 Socket 长连接
 * 2. 发送/接收消息
 * 3. 自动将收到的消息存入数据库
 */
object ChatClient {

    private const val TAG = "ChatClient"
    private const val PORT = 8888

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var isConnected = false

    //  Bug修复：添加 VideoRepository 用于自动添加消息发送者到联系人
    private var repository: VideoRepository? = null

    var currentUsername: String? = null
        private set

    // 连接状态 LiveData
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    // 接收消息 LiveData
    private val _newMessage = MutableLiveData<MessageEntity>()
    val newMessage: LiveData<MessageEntity> = _newMessage

    /**
     * 连接到服务器并登录
     *
     * @param serverIp 服务器IP
     * @param username 用户名
     * @param password 密码
     * @param callback 登录结果回调
     */
    fun connect(
        serverIp: String,
        username: String,
        password: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "connect: 开始连接服务器 $serverIp:$PORT")

                //  Bug修复：初始化 VideoRepository
                repository = VideoRepository(VideoPlayerApp.instance)

                // 创建 Socket 连接
                socket = Socket(serverIp, PORT)
                writer = PrintWriter(OutputStreamWriter(socket!!.getOutputStream(), "UTF-8"), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream(), "UTF-8"))

                isConnected = true
                _connectionState.postValue(ConnectionState.CONNECTED)
                Log.d(TAG, "connect: Socket 连接成功")

                // 发送登录请求
                val loginJson = JSONObject().apply {
                    put("type", "LOGIN")
                    put("user", username)
                    put("pass", password)
                }
                sendRaw(loginJson.toString())

                // 等待登录响应
                val response = reader?.readLine()
                Log.d(TAG, "connect: 收到登录响应: $response")

                if (response != null) {
                    val json = JSONObject(response)
                    val code = json.getInt("code")
                    val msg = json.getString("msg")

                    if (code == 200) {
                        currentUsername = username

                        //  Bug修复：保存当前登录用户ID到SessionManager
                        SessionManager.getInstance(VideoPlayerApp.instance).setCurrentUserId(username)

                        Log.d(TAG, "connect:  登录成功: $username")
                        callback(true, msg)

                        // 启动消息接收线程
                        startReceivingMessages()

                        //  Bug修复：登录成功后，自动添加消息发送者到联系人列表
                        autoAddMessageSendersToContacts()
                    } else {
                        Log.e(TAG, "connect:  登录失败: $msg")
                        disconnect()
                        callback(false, msg)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "connect: 连接失败", e)
                _connectionState.postValue(ConnectionState.DISCONNECTED)
                callback(false, "连接失败: ${e.message}")
            }
        }
    }

    /**
     * 发送消息
     *
     * @param to 接收者用户名
     * @param content 消息内容
     */
    fun sendMessage(to: String, content: String) {
        if (!isConnected || currentUsername == null) {
            Log.w(TAG, "sendMessage: 未连接或未登录")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val messageJson = JSONObject().apply {
                    put("type", "MSG")
                    put("from", currentUsername)
                    put("to", to)
                    put("content", content)
                    put("time", System.currentTimeMillis())
                }

                sendRaw(messageJson.toString())
                Log.d(TAG, "sendMessage: 消息已发送 -> $to: $content")

            } catch (e: Exception) {
                Log.e(TAG, "sendMessage: 发送失败", e)
            }
        }
    }

    /**
     * 启动消息接收线程
     */
    private fun startReceivingMessages() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "startReceivingMessages: 开始监听消息...")

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
                Log.e(TAG, "startReceivingMessages: 接收消息异常", e)
            } finally {
                _connectionState.postValue(ConnectionState.DISCONNECTED)
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private fun handleReceivedMessage(message: String) {
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

                    // 存入数据库
                    val messageEntity = MessageEntity(
                        senderId = from,
                        receiverId = to,
                        content = content,
                        timestamp = time
                    )

                    saveMessageToDatabase(messageEntity)
                    _newMessage.postValue(messageEntity)
                }

                "ACK" -> {
                    val code = json.getInt("code")
                    val msg = json.getString("msg")
                    Log.d(TAG, "handleReceivedMessage: 收到ACK响应 code=$code, msg=$msg")
                }

                else -> {
                    Log.w(TAG, "handleReceivedMessage: 未知消息类型: $type")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "handleReceivedMessage: 处理消息失败", e)
        }
    }

    /**
     * 保存消息到数据库
     */
    private fun saveMessageToDatabase(message: MessageEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                VideoPlayerApp.database.messageDao().insertMessage(message)
                Log.d(TAG, "saveMessageToDatabase:  消息已存入数据库")

                //  Bug修复：收到新消息后，自动添加发送者到联系人列表
                autoAddMessageSendersToContacts()
            } catch (e: Exception) {
                Log.e(TAG, "saveMessageToDatabase: 存储失败", e)
            }
        }
    }

    /**
     *  Bug修复：自动将发送过消息的用户添加到联系人列表
     *
     * 调用时机：
     * 1. 用户登录成功后（此时会收到离线消息）
     * 2. 收到新消息后
     */
    private fun autoAddMessageSendersToContacts() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addedCount = repository?.autoAddMessageSendersToContacts() ?: 0
                if (addedCount > 0) {
                    Log.d(TAG, "autoAddMessageSendersToContacts:  自动添加了 $addedCount 个新联系人")
                }
            } catch (e: Exception) {
                Log.e(TAG, "autoAddMessageSendersToContacts:  自动添加联系人失败", e)
            }
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
        currentUsername = null

        try {
            reader?.close()
            writer?.close()
            socket?.close()
            Log.d(TAG, "disconnect: 连接已断开")
        } catch (e: Exception) {
            Log.e(TAG, "disconnect: 断开连接失败", e)
        }

        _connectionState.postValue(ConnectionState.DISCONNECTED)
    }

    /**
     * 连接状态枚举
     */
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED
    }
}
