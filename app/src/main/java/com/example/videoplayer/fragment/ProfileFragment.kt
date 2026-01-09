package com.example.videoplayer.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.videoplayer.adapter.ProfilePagerAdapter
import com.example.videoplayer.databinding.FragmentProfileBinding
import com.example.videoplayer.utils.SessionManager
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 个人中心Fragment
 *
 * 使用TabLayout + ViewPager2展示用户信息和视频列表
 */
class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val MY_AVATAR_URL = "https://picsum.photos/200"  // 示例头像
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var pagerAdapter: ProfilePagerAdapter

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

        sessionManager = SessionManager.getInstance(requireContext())

        setupUserInfo()
        setupViewPager()

        Log.d(TAG, "onViewCreated: Fragment 初始化完成")
    }

    private fun setupUserInfo() {
        val currentUserId = sessionManager.currentUserId

        // 加载用户头像
        binding.ivAvatar.load(MY_AVATAR_URL) {
            crossfade(true)
            transformations(CircleCropTransformation())
            error(android.R.drawable.ic_menu_gallery)
            placeholder(android.R.drawable.ic_menu_gallery)
        }

        // 设置用户名和 ID
        binding.tvUserName.text = currentUserId
        binding.tvUserId.text = "用户ID: $currentUserId"
    }

    private fun setupViewPager() {
        // 创建ViewPager2适配器
        pagerAdapter = ProfilePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        // 连接TabLayout和ViewPager2
        val tabTitles = arrayOf("作品", "喜欢", "收藏")
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment 销毁视图")
        _binding = null
    }
}
