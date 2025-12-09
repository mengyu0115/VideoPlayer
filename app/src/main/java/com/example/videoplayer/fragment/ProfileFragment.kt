package com.example.videoplayer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.databinding.FragmentProfileBinding

/**
 * 个人中心Fragment
 *
 * 展示当前用户信息和功能入口
 */
class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val MY_USER_ID = "ME"
        private const val MY_USER_NAME = "当前用户"
        private const val MY_AVATAR_URL = "https://picsum.photos/200"  // 示例头像
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Fragment 创建视图")
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: 视图创建完成，开始初始化")

        setupUserInfo()
        setupClickListeners()

        Log.d(TAG, "onViewCreated: Fragment 初始化完成")
    }

    private fun setupUserInfo() {
        // 加载用户头像
        binding.ivAvatar.load(MY_AVATAR_URL) {
            crossfade(true)
            transformations(CircleCropTransformation())
            error(android.R.drawable.ic_menu_gallery)
            placeholder(android.R.drawable.ic_menu_gallery)
        }

        // 设置用户名和 ID
        binding.tvUserName.text = MY_USER_NAME
        binding.tvUserId.text = "用户ID: $MY_USER_ID"
    }

    private fun setupClickListeners() {
        // 个人信息
        binding.menuPersonalInfo.setOnClickListener {
            Log.d(TAG, "点击\"个人信息\"")
            Toast.makeText(requireContext(), "个人信息", Toast.LENGTH_SHORT).show()
        }

        // 我的收藏
        binding.menuFavorites.setOnClickListener {
            Log.d(TAG, "点击\"我的收藏\"")
            Toast.makeText(requireContext(), "我的收藏", Toast.LENGTH_SHORT).show()
        }

        // 浏览历史
        binding.menuHistory.setOnClickListener {
            Log.d(TAG, "点击\"浏览历史\"")
            Toast.makeText(requireContext(), "浏览历史", Toast.LENGTH_SHORT).show()
        }

        // 设置
        binding.menuSettings.setOnClickListener {
            Log.d(TAG, "点击\"设置\"")
            Toast.makeText(requireContext(), "设置", Toast.LENGTH_SHORT).show()
        }

        // 关于我们
        binding.menuAbout.setOnClickListener {
            Log.d(TAG, "点击\"关于我们\"")
            Toast.makeText(requireContext(), "关于我们", Toast.LENGTH_SHORT).show()
        }

        // 意见反馈
        binding.menuFeedback.setOnClickListener {
            Log.d(TAG, "点击\"意见反馈\"")
            Toast.makeText(requireContext(), "意见反馈", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment 销毁视图")
        _binding = null
    }
}
