package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * VideoLike DAO - 视频点赞数据访问对象
 *
 * 管理用户与视频的点赞关系：
 * - 查询点赞状态
 * - 添加/取消点赞
 * - 获取用户点赞的视频列表
 */
@Dao
interface VideoLikeDao {

    /**
     * 检查用户是否点赞了某个视频
     *
     * @param userId 用户ID
     * @param videoId 视频ID
     * @return Boolean 是否已点赞
     */
    @Query("SELECT EXISTS(SELECT 1 FROM video_likes WHERE userId = :userId AND videoId = :videoId)")
    suspend fun isVideoLiked(userId: String, videoId: String): Boolean

    /**
     * 添加点赞
     *
     * 如果已存在（复合主键冲突），则忽略
     *
     * @param like 点赞记录
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLike(like: VideoLikeEntity)

    /**
     * 取消点赞
     *
     * @param userId 用户ID
     * @param videoId 视频ID
     */
    @Query("DELETE FROM video_likes WHERE userId = :userId AND videoId = :videoId")
    suspend fun deleteLike(userId: String, videoId: String)

    /**
     * 获取用户点赞的所有视频ID列表
     *
     * 返回 Flow，当点赞数据变化时，UI 自动刷新
     *
     * @param userId 用户ID
     * @return Flow<List<String>> 视频ID列表的 Flow 流
     */
    @Query("SELECT videoId FROM video_likes WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLikedVideoIds(userId: String): Flow<List<String>>

    /**
     * 获取视频的点赞数
     *
     * @param videoId 视频ID
     * @return Int 点赞数
     */
    @Query("SELECT COUNT(*) FROM video_likes WHERE videoId = :videoId")
    suspend fun getLikeCountForVideo(videoId: String): Int

    /**
     * 获取用户点赞视频的总数
     *
     * @param userId 用户ID
     * @return Int 点赞视频总数
     */
    @Query("SELECT COUNT(*) FROM video_likes WHERE userId = :userId")
    suspend fun getUserLikeCount(userId: String): Int

    /**
     * 删除所有点赞记录
     *
     * 用于清空数据或测试
     */
    @Query("DELETE FROM video_likes")
    suspend fun deleteAllLikes()

    /**
     * 删除所有点赞记录（别名方法）
     */
    suspend fun deleteAll() = deleteAllLikes()
}
