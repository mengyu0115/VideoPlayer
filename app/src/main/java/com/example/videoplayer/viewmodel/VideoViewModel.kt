package com.example.videoplayer.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.videoplayer.data.CommentEntity
import com.example.videoplayer.data.UserEntity
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.repository.VideoRepository
import kotlinx.coroutines.launch

/**
 * 视频 ViewModel
 *
 * 架构：MVVM + Repository Pattern
 *
 * 职责：
 * - 为 UI 提供数据（通过 LiveData）
 * - 处理 UI 事件（刷新、点赞、评论、关注等）
 * - 协调 Repository 层
 *
 * 数据流：
 * Repository (Flow) -> ViewModel (LiveData) -> UI (观察)
 */
class VideoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VideoViewModel"
    }

    // Repository 实例
    private val repository: VideoRepository = VideoRepository(application)

    /**
     * 所有视频的 LiveData
     *
     * 数据源：repository.allVideos (Flow)
     * 自动观察：数据库变化时自动通知 UI
     *
     * 注意：Flow.asLiveData() 会自动管理生命周期
     */
    val videoList: LiveData<List<VideoEntity>> = repository.allVideos.asLiveData()

    init {
        Log.d(TAG, "init: ViewModel 初始化（使用 Repository Pattern）")

        //  已注释：现在使用 AppDatabase.onCreate 预填充数据，不需要在 init 时调用 refreshVideos()
        // 启动协程，刷新视频列表
        // viewModelScope.launch {
        //     try {
        //         Log.d(TAG, "init: 开始刷新视频数据...")
        //         repository.refreshVideos()
        //         Log.d(TAG, "init: 视频数据刷新完成")
        //     } catch (e: Exception) {
        //         Log.e(TAG, "init: 视频数据刷新失败", e)
        //     }
        // }
    }

    /**
     * 切换点赞状态
     *
     * 用户点击点赞按钮时调用
     *
     * @param videoId 视频 ID
     * @param isLiked 是否已点赞
     */
    fun toggleLike(videoId: String, isLiked: Boolean) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "toggleLike: videoId=$videoId, isLiked=$isLiked")
                repository.toggleLike(videoId, isLiked)
                Log.d(TAG, "toggleLike: 点赞状态已更新")
            } catch (e: Exception) {
                Log.e(TAG, "toggleLike: 点赞状态更新失败", e)
            }
        }
    }

    /**
     * 切换收藏状态
     *
     * 用户点击收藏按钮时调用
     *
     * @param videoId 视频 ID
     * @param isFavorite 是否已收藏
     */
    fun toggleFavorite(videoId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "toggleFavorite: videoId=$videoId, isFavorite=$isFavorite")
                repository.toggleFavorite(videoId, isFavorite)
                Log.d(TAG, "toggleFavorite: 收藏状态已更新")
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite: 收藏状态更新失败", e)
            }
        }
    }

    /**
     * 获取所有收藏的视频
     *
     * @return LiveData<List<VideoEntity>> 收藏视频列表的 LiveData
     */
    fun getFavoriteVideos(): LiveData<List<VideoEntity>> {
        Log.d(TAG, "getFavoriteVideos: 获取收藏列表")
        return repository.getFavoriteVideos().asLiveData()
    }

    /**
     * 手动刷新视频列表
     *
     * 用户下拉刷新时调用
     */
    fun refreshVideos() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "refreshVideos: 手动刷新视频数据...")
                repository.refreshVideos()
                Log.d(TAG, "refreshVideos: 视频数据刷新完成")
            } catch (e: Exception) {
                Log.e(TAG, "refreshVideos: 视频数据刷新失败", e)
            }
        }
    }

    // ========== 社交功能：评论 ==========

    /**
     * 获取指定视频的评论列表
     *
     * @param videoId 视频 ID
     * @return LiveData<List<CommentEntity>> 评论列表的 LiveData
     */
    fun getCommentsForVideo(videoId: String): LiveData<List<CommentEntity>> {
        Log.d(TAG, "getCommentsForVideo: videoId=$videoId")
        return repository.getCommentsForVideo(videoId).asLiveData()
    }

    /**
     * 发送评论
     *
     * @param videoId 视频 ID
     * @param content 评论内容
     */
    suspend fun sendComment(videoId: String, content: String) {
        try {
            Log.d(TAG, "sendComment: videoId=$videoId, content=$content")
            repository.sendComment(videoId, content)
            Log.d(TAG, "sendComment: 评论发送成功")
        } catch (e: Exception) {
            Log.e(TAG, "sendComment: 评论发送失败", e)
            throw e
        }
    }

    // ========== 社交功能：关注 ==========

    /**
     * 获取所有关注的用户ID列表（迭代13修复：多用户数据隔离）
     *
     * 架构变更：返回类型从 List<UserEntity> 改为 List<String>
     * 原因：使用 UserFollowDao 只存储关注关系，不存储用户详细信息
     *
     * @return LiveData<List<String>> 当前用户关注的用户ID列表
     */
    fun getAllFollowedUsers(): LiveData<List<String>> {
        Log.d(TAG, "getAllFollowedUsers: 获取关注列表")
        return repository.getAllFollowedUsers().asLiveData()
    }

    /**
     * 切换关注状态（乐观 UI）
     *
     * @param userId 用户 ID
     * @param userName 用户昵称
     * @param avatarUrl 用户头像 URL
     * @param onResult 回调结果（true 表示已关注，false 表示已取消关注）
     */
    fun toggleFollow(
        userId: String,
        userName: String,
        avatarUrl: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "toggleFollow: userId=$userId, userName=$userName")
                val isFollowing = repository.toggleFollow(userId, userName, avatarUrl)
                Log.d(TAG, "toggleFollow: 关注状态已更新, isFollowing=$isFollowing")
                onResult(isFollowing)
            } catch (e: Exception) {
                Log.e(TAG, "toggleFollow: 关注状态更新失败", e)
            }
        }
    }

    /**
     * 检查是否已关注某用户
     *
     * @param userId 用户 ID
     * @param onResult 回调结果
     */
    fun checkIsFollowing(userId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val isFollowing = repository.isFollowing(userId)
                Log.d(TAG, "checkIsFollowing: userId=$userId, isFollowing=$isFollowing")
                onResult(isFollowing)
            } catch (e: Exception) {
                Log.e(TAG, "checkIsFollowing: 检查关注状态失败", e)
                onResult(false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared: ViewModel 被清除")
    }
}
