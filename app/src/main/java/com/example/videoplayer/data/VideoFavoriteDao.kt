package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * VideoFavorite DAO - 视频收藏数据访问对象
 *
 * 管理用户与视频的收藏关系：
 * - 查询收藏状态
 * - 添加/取消收藏
 * - 获取用户收藏的视频列表
 */
@Dao
interface VideoFavoriteDao {

    /**
     * 检查用户是否收藏了某个视频
     *
     * @param userId 用户ID
     * @param videoId 视频ID
     * @return Boolean 是否已收藏
     */
    @Query("SELECT EXISTS(SELECT 1 FROM video_favorites WHERE userId = :userId AND videoId = :videoId)")
    suspend fun isVideoFavorited(userId: String, videoId: String): Boolean

    /**
     * 添加收藏
     *
     * 如果已存在（复合主键冲突），则忽略
     *
     * @param favorite 收藏记录
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: VideoFavoriteEntity)

    /**
     * 取消收藏
     *
     * @param userId 用户ID
     * @param videoId 视频ID
     */
    @Query("DELETE FROM video_favorites WHERE userId = :userId AND videoId = :videoId")
    suspend fun deleteFavorite(userId: String, videoId: String)

    /**
     * 获取用户收藏的所有视频ID列表
     *
     * 返回 Flow，当收藏数据变化时，UI 自动刷新
     *
     * @param userId 用户ID
     * @return Flow<List<String>> 视频ID列表的 Flow 流
     */
    @Query("SELECT videoId FROM video_favorites WHERE userId = :userId ORDER BY timestamp DESC")
    fun getFavoritedVideoIds(userId: String): Flow<List<String>>

    /**
     * 获取用户收藏视频的总数
     *
     * @param userId 用户ID
     * @return Int 收藏视频总数
     */
    @Query("SELECT COUNT(*) FROM video_favorites WHERE userId = :userId")
    suspend fun getUserFavoriteCount(userId: String): Int

    /**
     * 删除所有收藏记录
     *
     * 用于清空数据或测试
     */
    @Query("DELETE FROM video_favorites")
    suspend fun deleteAllFavorites()

    /**
     * 删除所有收藏记录（别名方法）
     */
    suspend fun deleteAll() = deleteAllFavorites()
}
