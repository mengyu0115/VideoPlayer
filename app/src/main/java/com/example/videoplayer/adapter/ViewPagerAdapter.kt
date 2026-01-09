package com.example.videoplayer.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.videoplayer.fragment.ContactsFragment
import com.example.videoplayer.fragment.ProfileFragment
import com.example.videoplayer.fragment.VideoFragment

/**
 * ViewPager2 适配器
 *
 * 管理三个主页面 Fragment：
 * - 0: VideoFragment（首页/视频流）
 * - 1: ContactsFragment（好友/关注列表）
 * - 2: ProfileFragment（我的/个人中心）
 */
class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3  // 三个页面

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> VideoFragment()        // 首页：视频流
            1 -> ContactsFragment()     // 好友：关注列表
            2 -> ProfileFragment()      // 我的：个人中心
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
