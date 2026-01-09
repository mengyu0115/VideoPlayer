package com.example.videoplayer.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.videoplayer.fragment.profile.FavoritesFragment
import com.example.videoplayer.fragment.profile.LikesFragment
import com.example.videoplayer.fragment.profile.WorksFragment

/**
 * ProfilePagerAdapter - ViewPager2适配器
 *
 * 管理三个Tab页面：作品、喜欢、收藏
 */
class ProfilePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> WorksFragment.newInstance()      // 作品
            1 -> LikesFragment.newInstance()      // 喜欢
            2 -> FavoritesFragment.newInstance()  // 收藏
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}
