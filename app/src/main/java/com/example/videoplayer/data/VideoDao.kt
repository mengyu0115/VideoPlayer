package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 视频数据访问对象（DAO）
 *
 * 提供数据库操作接口：
 * - 查询所有视频（Flow 自动刷新）
 * - 插入/更新视频数据
 * - 单独更新点赞状态（性能优化）
 */
@Dao
interface VideoDao {

    /**
     * 获取所有视频
     *
     * 返回 Flow，当数据库数据变化时，UI 自动刷新
     * 这是 SSOT (Single Source of Truth) 的核心
     *
     * @return Flow<List<VideoEntity>> 所有视频的 Flow 流
     */
    @Query("SELECT * FROM videos")
    fun getAllVideos(): Flow<List<VideoEntity>>

    /**
     * 插入视频列表
     *
     * 如果 id 冲突，则替换旧数据（用于网络刷新场景）
     *
     * @param videos 要插入的视频列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    /**
     * 插入单个视频
     *
     * 如果 id 冲突，则替换旧数据
     *
     * @param video 要插入的视频
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    /**
     * 更新点赞数（UI 展示用）
     *
     * 当用户点赞/取消点赞时，同步更新点赞数
     *
     * @param videoId 视频 ID
     * @param likeCount 新的点赞数
     */
    @Query("UPDATE videos SET likeCount = :likeCount WHERE id = :videoId")
    suspend fun updateLikeCount(videoId: String, likeCount: Int)

    /**
     * 更新评论数（UI 展示用）
     *
     * 当用户发送评论时，同步更新评论数
     *
     * @param videoId 视频 ID
     * @param commentCount 新的评论数
     */
    @Query("UPDATE videos SET commentCount = :commentCount WHERE id = :videoId")
    suspend fun updateCommentCount(videoId: String, commentCount: Int)

    /**
     * 删除所有视频
     *
     * 用于清空缓存或重新加载数据
     */
    @Query("DELETE FROM videos")
    suspend fun deleteAllVideos()

    /**
     * 删除所有视频（别名方法）
     */
    suspend fun deleteAll() = deleteAllVideos()

    /**
     * 根据 ID 获取单个视频
     *
     * @param videoId 视频 ID
     * @return VideoEntity? 视频实体，不存在则返回 null
     */
    @Query("SELECT * FROM videos WHERE id = :videoId")
    suspend fun getVideoById(videoId: String): VideoEntity?

    /**
     * 获取所有收藏的视频（迭代13：已废弃）
     *
     *  迭代13：isFavorite 字段已标记为 @Ignore，不再存储到数据库
     * 此方法将返回所有视频，后续需重构为使用关联表查询
     *
     * @return Flow<List<VideoEntity>> 收藏视频列表的 Flow 流
     */
    @Deprecated("迭代13：isFavorite 字段不再存储，需重构为使用关联表查询")
    @Query("SELECT * FROM videos")
    fun getFavoriteVideos(): Flow<List<VideoEntity>>

    /**
     * 更新收藏状态（迭代13：临时保留）
     *
     *  此方法暂时保留以避免编译错误，但不再实际更新数据库
     * 后续需重构为使用关联表
     *
     * @param videoId 视频 ID
     * @param isFavorite 是否已收藏
     */
    @Deprecated("迭代13：isFavorite 字段不再存储，此方法无效")
    suspend fun updateFavoriteStatus(videoId: String, isFavorite: Boolean) {
        // 空实现，避免编译错误
    }

    /**
     * 查找所有未绑定作者的视频（authorId 为空）
     *
     * 用于数据迁移：将旧视频绑定到现有用户
     *
     * @return List<VideoEntity> 未绑定作者的视频列表
     * 注：这是测试方法！！！实际应用中应该使用关联表查询
     */
    @Query("SELECT * FROM videos WHERE authorId = ''")
    suspend fun getVideosWithoutAuthor(): List<VideoEntity>

    /**
     * 更新视频的作者信息
     *
     * 用于数据迁移：为视频绑定作者
     *
     * @param videoId 视频 ID
     * @param authorId 作者 ID
     * @param authorName 作者昵称
     * @param authorAvatarUrl 作者头像 URL
     */
    @Query("UPDATE videos SET authorId = :authorId, authorName = :authorName, authorAvatarUrl = :authorAvatarUrl WHERE id = :videoId")
    suspend fun updateVideoAuthor(videoId: String, authorId: String, authorName: String, authorAvatarUrl: String)
}
