package com.example.videoplayer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 评论数据库实体
 *
 * 用于存储视频评论数据（多用户共享）
 * 任何用户登录都能看到某视频下的所有评论
 *
 * 字段说明：
 * @param id 评论 ID（主键）
 * @param videoId 视频 ID（外键，关联 VideoEntity）
 * @param userId 评论者用户ID
 * @param content 评论内容
 * @param userName 评论用户昵称
 * @param avatarUrl 评论用户头像 URL
 * @param timestamp 评论时间戳（毫秒）
 */
@Entity(
    tableName = "comments",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE  // 视频删除时，关联评论自动删除
        )
    ],
    indices = [
        Index(value = ["videoId"])  // 为 videoId 创建索引，优化查询性能
    ]
)
data class CommentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,                // 评论 ID（自动生成）
    val videoId: String,             // 视频 ID（外键）
    val userId: String,              // 评论者用户ID（新增）
    val content: String,             // 评论内容
    val userName: String,            // 评论用户昵称
    val avatarUrl: String,           // 评论用户头像 URL
    val timestamp: Long = System.currentTimeMillis()  // 评论时间戳
)
