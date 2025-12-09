package com.example.videoplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关注用户数据库实体
 *
 * 用于存储我关注的用户列表
 *
 * 设计理念：
 * - 这张表只存我主动关注的用户
 * - 当用户点击"关注"按钮时，插入该用户信息
 * - 当用户点击"取消关注"时，删除该用户记录
 *
 * 字段说明：
 * @param userId 用户 ID（主键）
 * @param userName 用户昵称
 * @param avatarUrl 用户头像 URL
 * @param isFollowing 是否正在关注（默认 true）
 * @param followedAt 关注时间戳（毫秒）
 */
@Entity(tableName = "followed_users")
data class UserEntity(
    @PrimaryKey
    val userId: String,              // 用户 ID（主键，对应视频作者 ID）
    val userName: String,            // 用户昵称
    val avatarUrl: String,           // 用户头像 URL
    val isFollowing: Boolean = true, // 是否正在关注（默认 true）
    val followedAt: Long = System.currentTimeMillis()  // 关注时间戳
)
