package com.example.videoplayer.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoplayer.adapter.CommentAdapter
import com.example.videoplayer.databinding.BottomSheetCommentBinding
import com.example.videoplayer.viewmodel.VideoViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * 评论弹窗
 *
 * 使用 BottomSheetDialogFragment 实现 Material Design 风格的评论区
 *
 * 功能：
 * - 展示视频评论列表
 * - 发送评论
 * - 自动弹出键盘
 */
class CommentBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "CommentBottomSheet"
        private const val ARG_VIDEO_ID = "video_id"

        /**
         * 创建实例
         *
         * @param videoId 视频 ID
         * @return CommentBottomSheet 实例
         */
        fun newInstance(videoId: String): CommentBottomSheet {
            return CommentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_VIDEO_ID, videoId)
                }
            }
        }
    }

    private var _binding: BottomSheetCommentBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels 获取 Activity 级别的 ViewModel
    private val viewModel: VideoViewModel by activityViewModels()

    private lateinit var commentAdapter: CommentAdapter
    private lateinit var videoId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoId = arguments?.getString(ARG_VIDEO_ID) ?: ""
        Log.d(TAG, "onCreate: videoId=$videoId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCommentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: 评论弹窗创建")

        setupRecyclerView()
        observeComments()
        setupSendButton()

        // 延迟弹出键盘（等待布局完成）
        binding.etComment.postDelayed({
            showKeyboard()
        }, 300)
    }

    /**
     * 设置 RecyclerView
     */
    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter()

        binding.recyclerViewComments.apply {
            adapter = commentAdapter
            layoutManager = LinearLayoutManager(requireContext())
            // 设置固定大小优化性能
            setHasFixedSize(true)
        }

        Log.d(TAG, "setupRecyclerView: RecyclerView 配置完成")
    }

    /**
     * 观察评论数据
     */
    private fun observeComments() {
        Log.d(TAG, "observeComments: 开始观察评论数据 videoId=$videoId")

        viewModel.getCommentsForVideo(videoId).observe(viewLifecycleOwner) { comments ->
            Log.d(TAG, "observeComments: 收到评论数据，数量: ${comments.size}")

            // 更新评论列表
            commentAdapter.submitList(comments)

            // 更新评论数显示
            binding.tvCommentCount.text = "${comments.size}条评论"

            // 如果有评论，滚动到最新评论（第一条）
            if (comments.isNotEmpty()) {
                binding.recyclerViewComments.scrollToPosition(0)
            }
        }
    }

    /**
     * 设置发送按钮
     */
    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val content = binding.etComment.text.toString().trim()

            if (content.isEmpty()) {
                Toast.makeText(requireContext(), "请输入评论内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 发送评论
            sendComment(content)
        }
    }

    /**
     * 发送评论
     *
     * @param content 评论内容
     */
    private fun sendComment(content: String) {
        Log.d(TAG, "sendComment: 发送评论 content=$content")

        lifecycleScope.launch {
            try {
                // 调用 ViewModel 发送评论
                viewModel.sendComment(videoId, content)

                // 清空输入框
                binding.etComment.text?.clear()

                // 提示成功
                Toast.makeText(requireContext(), "评论成功", Toast.LENGTH_SHORT).show()

                Log.d(TAG, "sendComment:  评论发送成功")

            } catch (e: Exception) {
                Log.e(TAG, "sendComment:  评论发送失败", e)
                Toast.makeText(requireContext(), "评论失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 弹出键盘
     */
    private fun showKeyboard() {
        binding.etComment.requestFocus()
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.showSoftInput(binding.etComment, InputMethodManager.SHOW_IMPLICIT)
        Log.d(TAG, "showKeyboard: 键盘已弹出")
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.etComment.windowToken, 0)
        Log.d(TAG, "hideKeyboard: 键盘已隐藏")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideKeyboard()
        _binding = null
        Log.d(TAG, "onDestroyView: 评论弹窗销毁")
    }
}
