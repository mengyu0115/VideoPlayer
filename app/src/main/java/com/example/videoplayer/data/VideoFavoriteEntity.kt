package com.example.videoplayer.data

import androidx.room.Entity

/**
 * VideoFavorite Entity - 视频收藏关联表
 *
 * 实现多用户收藏隔离：
 * - 记录"谁"收藏了"哪个视频"
 * - 每个用户有独立的收藏列表
 * - 支持多用户并发场景
 *
 * 复合主键：(userId, videoId)
 * 同一用户对同一视频只能收藏一次
 */
@Entity(
    tableName = "video_favorites",
    primaryKeys = ["userId", "videoId"]
)
data class VideoFavoriteEntity(
    /**
     * 用户ID - 谁收藏的
     */
    val userId: String,

    /**
     * 视频ID - 收藏了哪个视频
     */
    val videoId: String,

    /**
     * 收藏时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)
