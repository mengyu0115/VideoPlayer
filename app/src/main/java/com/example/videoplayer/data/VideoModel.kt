package com.example.videoplayer.data

/**
 * 视频数据模型
 *
 * 字段说明：
 * @param id 视频 ID
 * @param title 视频标题
 * @param coverUrl 封面图 URL
 * @param videoUrl 视频 URL
 * @param userName 发布者昵称
 * @param description 视频简介/文案
 * @param likeCount 点赞数
 * @param commentCount 评论数
 * @param shareCount 分享数
 * @param userAvatarUrl 用户头像 URL
 */
data class VideoModel(
    val id: Int,
    val title: String,
    val coverUrl: String,
    val videoUrl: String,
    val userName: String,              // 新增：发布者昵称
    val description: String,           // 新增：视频描述
    val likeCount: Int,                // 新增：点赞数
    val commentCount: Int,             // 新增：评论数
    val shareCount: Int = 0,           // 新增：分享数（默认0）
    val userAvatarUrl: String          // 新增：用户头像 URL
)
