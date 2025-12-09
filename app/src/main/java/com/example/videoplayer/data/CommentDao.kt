package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 评论数据访问对象（DAO）
 *
 * 提供评论相关的数据库操作接口
 */
@Dao
interface CommentDao {

    /**
     * 获取指定视频的所有评论
     *
     * 返回 Flow，当评论数据变化时，UI 自动刷新
     * 按时间倒序排列（最新评论在前）
     *
     * @param videoId 视频 ID
     * @return Flow<List<CommentEntity>> 评论列表的 Flow 流
     */
    @Query("SELECT * FROM comments WHERE videoId = :videoId ORDER BY timestamp DESC")
    fun getCommentsForVideo(videoId: String): Flow<List<CommentEntity>>

    /**
     * 插入评论
     *
     * 如果 id 冲突，则替换旧评论
     *
     * @param comment 要插入的评论
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    /**
     * 批量插入评论
     *
     * 用于初始化测试数据
     *
     * @param comments 要插入的评论列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    /**
     * 删除指定视频的所有评论
     *
     * @param videoId 视频 ID
     */
    @Query("DELETE FROM comments WHERE videoId = :videoId")
    suspend fun deleteCommentsForVideo(videoId: String)

    /**
     * 删除指定评论
     *
     * @param commentId 评论 ID
     */
    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteCommentById(commentId: Long)

    /**
     * 获取指定视频的评论数
     *
     * @param videoId 视频 ID
     * @return Int 评论数
     */
    @Query("SELECT COUNT(*) FROM comments WHERE videoId = :videoId")
    suspend fun getCommentCountForVideo(videoId: String): Int

    /**
     * 删除所有评论
     *
     * 用于清空数据或测试
     */
    @Query("DELETE FROM comments")
    suspend fun deleteAllComments()

    /**
     * 删除所有评论（别名方法）
     */
    suspend fun deleteAll() = deleteAllComments()
}
