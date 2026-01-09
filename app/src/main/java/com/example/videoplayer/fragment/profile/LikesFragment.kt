package com.example.videoplayer.fragment.profile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.videoplayer.VideoPlayerActivity
import com.example.videoplayer.adapter.GridVideoAdapter
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.databinding.FragmentVideoListBinding
import com.example.videoplayer.repository.VideoRepository
import kotlinx.coroutines.launch

/**
 * 喜欢Fragment - 显示用户点赞的视频
 */
class LikesFragment : Fragment() {

    companion object {
        private const val TAG = "LikesFragment"

        fun newInstance() = LikesFragment()
    }

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var videoRepository: VideoRepository
    private lateinit var adapter: GridVideoAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        videoRepository = VideoRepository(requireContext())

        setupRecyclerView()
        loadVideos()
    }

    private fun setupRecyclerView() {
        adapter = GridVideoAdapter { video ->
            onVideoClick(video)
        }

        // 三列瀑布流
        val layoutManager = StaggeredGridLayoutManager(
            3,
            StaggeredGridLayoutManager.VERTICAL
        )

        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
    }

    private fun loadVideos() {
        Log.d(TAG, "loadVideos: 加载我点赞的视频")
        lifecycleScope.launch {
            videoRepository.getLikedVideos().collect { videos ->
                Log.d(TAG, "loadVideos: 收到 ${videos.size} 个点赞视频")
                updateUI(videos)
            }
        }
    }

    private fun updateUI(videos: List<VideoEntity>) {
        if (videos.isEmpty()) {
            binding.recyclerView.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "还没有点赞内容"
        } else {
            binding.recyclerView.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            adapter.submitList(videos)
        }
    }

    private fun onVideoClick(video: VideoEntity) {
        Log.d(TAG, "onVideoClick: 点击视频 ${video.id}")
        val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
            putExtra(VideoPlayerActivity.EXTRA_VIDEO_ID, video.id)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
