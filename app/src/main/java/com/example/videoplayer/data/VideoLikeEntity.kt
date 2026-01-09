package com.example.videoplayer.data

import androidx.room.Entity

/**
 * VideoLike Entity - 视频点赞关联表
 *
 * 实现多用户点赞隔离：
 * - 记录"谁"点赞了"哪个视频"
 * - 每个用户有独立的点赞列表
 * - 支持多用户并发场景
 *
 * 复合主键：(userId, videoId)
 * 同一用户对同一视频只能点赞一次
 */
@Entity(
    tableName = "video_likes",
    primaryKeys = ["userId", "videoId"]
)
data class VideoLikeEntity(
    /**
     * 用户ID - 谁点赞的
     */
    val userId: String,

    /**
     * 视频ID - 点赞了哪个视频
     */
    val videoId: String,

    /**
     * 点赞时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)
