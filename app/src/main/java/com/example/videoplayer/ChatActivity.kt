package com.example.videoplayer

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoplayer.adapter.EmojiAdapter
import com.example.videoplayer.adapter.MessageAdapter
import com.example.videoplayer.databinding.ActivityChatBinding
import com.example.videoplayer.viewmodel.ChatViewModel

/**
 * 聊天Activity
 *
 * 点对点聊天页面，支持：
 * - 发送消息
 * - 接收消息
 * - 表情面板
 * - 真实 Socket 通信
 */
class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"

        // 常用表情列表
        private val EMOJI_LIST = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
            "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
            "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
            "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥",
            "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
            "🤢", "🤮", "🤧", "🥵", "🥶", "😎", "🤓", "🧐",
            "😕", "😟", "🙁", "☹️", "😮", "😯", "😲", "😳",
            "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
            "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
            "👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙",
            "👏", "🙌", "👐", "🤲", "🙏", "💪", "❤️", "🧡",
            "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔"
        )
    }

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var emojiAdapter: EmojiAdapter

    private lateinit var targetUserId: String
    private lateinit var targetUserName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: ChatActivity 开始创建")

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 获取传入的用户信息
        targetUserId = intent.getStringExtra("userId") ?: ""
        targetUserName = intent.getStringExtra("userName") ?: "未知用户"

        Log.d(TAG, "onCreate: 对方用户 - userId=$targetUserId, userName=$targetUserName")

        if (targetUserId.isEmpty()) {
            Log.e(TAG, "onCreate: userId 为空，Activity 异常")
            finish()
            return
        }
        // 标题栏
        setupToolbar()
        // 消息列表
        setupRecyclerView()
        // 表情面板
        setupEmojiPanel()
        // 输入区域
        setupInputArea()
        // 监听消息
        observeMessages()

        Log.d(TAG, "onCreate: ChatActivity 创建完成")
    }

    /**
     * 配置标题栏
     */
    private fun setupToolbar() {
        binding.tvUserName.text = targetUserName

        // 返回按钮
        binding.ivBack.setOnClickListener {
            Log.d(TAG, "点击返回按钮")
            finish()
        }
    }

    /**
     * 配置消息列表
     */
    private fun setupRecyclerView() {
        //  传入当前用户ID，用于判断消息的左右显示
        val currentUserId = com.example.videoplayer.utils.SessionManager
            .getInstance(this)
            .currentUserId
        messageAdapter = MessageAdapter(currentUserId)

        binding.rvMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@ChatActivity)
        }
    }

    /**
     * 配置表情面板
     */
    private fun setupEmojiPanel() {
        emojiAdapter = EmojiAdapter(EMOJI_LIST) { emoji ->
            // 点击表情时插入到输入框
            val currentText = binding.etMessage.text.toString()
            binding.etMessage.setText(currentText + emoji)
            binding.etMessage.setSelection(binding.etMessage.text.length)
        }

        binding.rvEmojis.apply {
            adapter = emojiAdapter
            layoutManager = GridLayoutManager(this@ChatActivity, 8)
        }

        // 表情按钮点击事件
        binding.btnEmoji.setOnClickListener {
            toggleEmojiPanel()
        }
    }

    /**
     * 切换表情面板显示/隐藏
     */
    private fun toggleEmojiPanel() {
        if (binding.emojiPanel.visibility == View.GONE) {
            binding.emojiPanel.visibility = View.VISIBLE
            Log.d(TAG, "toggleEmojiPanel: 显示表情面板")
        } else {
            binding.emojiPanel.visibility = View.GONE
            Log.d(TAG, "toggleEmojiPanel: 隐藏表情面板")
        }
    }

    /**
     * 配置输入区域
     */
    private fun setupInputArea() {
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()

            if (content.isEmpty()) {
                Log.d(TAG, "输入内容为空，忽略发送")
                return@setOnClickListener
            }

            Log.d(TAG, "发送消息: content=$content")

            // 发送消息
            viewModel.sendMessage(targetUserId, content)

            // 清空输入框
            binding.etMessage.text.clear()

            // 隐藏表情面板
            binding.emojiPanel.visibility = View.GONE
        }
    }

    /**
     * 观察消息列表
     */
    private fun observeMessages() {
        Log.d(TAG, "observeMessages: 开始观察消息列表，对方用户=$targetUserId")

        //观察者模式
        viewModel.getMessages(targetUserId).observe(this) { messages ->
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "observeMessages:  LiveData 触发！收到消息列表，数量=${messages.size}")

            // 打印每条消息的详细信息
            messages.forEachIndexed { index, msg ->
                Log.d(TAG, "observeMessages: [$index] id=${msg.id}, from=${msg.senderId} to=${msg.receiverId}, content=${msg.content}, timestamp=${msg.timestamp}")
            }

            // 转换为带时间戳的列表
            val items = MessageAdapter.convertToItems(messages)
            Log.d(TAG, "observeMessages: 转换后items数量=${items.size}")

            //submitList方法用于提交新的列表数据，并触发列表的更新
            messageAdapter.submitList(items) {
                Log.d(TAG, "observeMessages: submitList 完成回调")

                // 滚动到最新消息
                if (items.isNotEmpty()) {
                    binding.rvMessages.smoothScrollToPosition(items.size - 1)
                    Log.d(TAG, "observeMessages: 滚动到位置 ${items.size - 1}")
                }
            }
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        // 标记消息为已读
        viewModel.markAsRead(targetUserId)
        Log.d(TAG, "observeMessages: 已调用 markAsRead($targetUserId)")
    }
}
