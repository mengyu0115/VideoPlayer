package com.example.videoplayer.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.videoplayer.ChatActivity
import com.example.videoplayer.adapter.ContactAdapter
import com.example.videoplayer.databinding.FragmentContactsBinding
import com.example.videoplayer.repository.VideoRepository
import com.example.videoplayer.viewmodel.VideoViewModel
import kotlinx.coroutines.launch

/**
 * 联系人Fragment
 *
 * 展示关注的用户列表，点击可以进入聊天页面
 */
class ContactsFragment : Fragment() {

    companion object {
        private const val TAG = "ContactsFragment"
    }

    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    // 使用 activityViewModels() 与 Activity 共享 ViewModel
    private val viewModel: VideoViewModel by activityViewModels()

    private lateinit var contactAdapter: ContactAdapter
    private lateinit var repository: VideoRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView: Fragment 创建视图")
        // 初始化视图绑定
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: 视图创建完成，开始初始化")

        // 初始化 Repository
        repository = VideoRepository(requireContext())

        setupRecyclerView()
        observeViewModel()

        Log.d(TAG, "onViewCreated: Fragment 初始化完成")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Fragment 恢复，检查并添加消息发送者到联系人")

        //  每次恢复时，自动检查并添加给我发送过消息的用户到联系人列表
        lifecycleScope.launch {
            try {
                val addedCount = repository.autoAddMessageSendersToContacts()
                if (addedCount > 0) {
                    Log.d(TAG, "onResume:  已自动添加 $addedCount 个新联系人")
                }
            } catch (e: Exception) {
                Log.e(TAG, "onResume:  自动添加联系人失败", e)
            }
        }
    }

    private fun setupRecyclerView() {
        contactAdapter = ContactAdapter()

        // 点击联系人进入聊天页面
        contactAdapter.onItemClick = { user ->
            Log.d(TAG, "点击联系人: userId=${user.userId}, userName=${user.userName}")

            // 跳转到 ChatActivity
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra("userId", user.userId)
                putExtra("userName", user.userName)
                putExtra("avatarUrl", user.avatarUrl)
            }
            startActivity(intent)
        }

        binding.rvContacts.apply {
            adapter = contactAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    // 观察 ViewModel，若有新关注列表，则更新联系人列表
    private fun observeViewModel() {
        // 观察关注列表（迭代13修复：多用户数据隔离）
        viewModel.getAllFollowedUsers().observe(viewLifecycleOwner) { userIds ->
            Log.d(TAG, "观察到关注列表变化: 数量=${userIds.size}, 用户IDs=$userIds")

            if (userIds.isEmpty()) {
                // 显示空状态
                binding.rvContacts.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
            } else {
                // 显示列表
                binding.rvContacts.visibility = View.VISIBLE
                binding.tvEmpty.visibility = View.GONE

                //  修复：将用户ID列表转换为UserEntity列表
                // TODO: 后续优化 - 从服务器获取用户详细信息或维护本地用户信息缓存
                val userEntities = userIds.map { userId ->
                    com.example.videoplayer.data.UserEntity(
                        userId = userId,
                        userName = userId,  // 使用ID作为昵称
                        avatarUrl = com.example.videoplayer.network.NetworkConfig.getDefaultAvatarUrl(userId),
                        isFollowing = true,
                        followedAt = System.currentTimeMillis()
                    )
                }
                //将用户列表提交给Adapter
                contactAdapter.submitList(userEntities)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView: Fragment 销毁视图")
        _binding = null
    }
}
