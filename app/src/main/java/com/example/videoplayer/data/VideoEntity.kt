package com.example.videoplayer.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * 视频数据库实体（迭代13重构）
 *
 * Room 数据库表结构，用于数据持久化
 * 实现离线模式和状态保存
 *
 *  迭代13架构变更：
 * - isLiked 和 isFavorite 不再存储到数据库（@Ignore 标记）
 * - 这些状态在运行时通过关联表（video_likes）动态查询组装
 * - 实现多用户数据隔离
 *
 * 字段说明：
 * @param id 视频 ID（主键）
 * @param title 视频标题
 * @param videoUrl 视频播放 URL
 * @param coverUrl 封面图 URL
 * @param authorId 作者 ID（关联 UserEntity.userId）
 * @param authorName 发布者昵称（对应 UI 的 userName）
 * @param authorAvatarUrl 用户头像 URL（对应 UI 的 userAvatarUrl）
 * @param description 视频文案/简介
 * @param likeCount 点赞数
 * @param commentCount 评论数
 * @param isLiked 当前用户是否已点赞（运行时组装，不存储）
 * @param isFavorite 当前用户是否已收藏（运行时组装，不存储）
 * @param isMine 是否是当前用户发布的视频（用于"我的"页面）
 */
@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val videoUrl: String,
    val coverUrl: String,
    val authorId: String,            // 作者 ID
    val authorName: String,          // 发布者昵称
    val authorAvatarUrl: String,     // 用户头像 URL
    val description: String,          // 视频文案
    val likeCount: Int,              // 点赞数
    val commentCount: Int,           // 评论数

    //  迭代13：这些字段不存储到数据库，由 Repository 层运行时组装
    @Ignore
    val isMine: Boolean = false,     // 是否是我发布的视频（运行时计算 authorId == currentUserId）
    @Ignore
    val isLiked: Boolean = false,    // 是否已点赞（运行时查询 video_likes 表）
    @Ignore
    val isFavorite: Boolean = false  // 是否已收藏（运行时查询 video_likes 表）
) {
    /**
     * 辅助构造函数（用于 Room 从数据库读取数据）
     *
     * Room 需要一个不包含 @Ignore 字段的构造函数
     * 从数据库读取时，@Ignore 字段使用默认值
     */
    constructor(
        id: String,
        title: String,
        videoUrl: String,
        coverUrl: String,
        authorId: String,
        authorName: String,
        authorAvatarUrl: String,
        description: String,
        likeCount: Int,
        commentCount: Int
    ) : this(
        id = id,
        title = title,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        authorId = authorId,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        description = description,
        likeCount = likeCount,
        commentCount = commentCount,
        isMine = false,     // 默认值，由 Repository 层覆盖
        isLiked = false,    // 默认值，由 Repository 层覆盖
        isFavorite = false  // 默认值，由 Repository 层覆盖
    )
}
