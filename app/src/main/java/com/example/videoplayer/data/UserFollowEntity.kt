package com.example.videoplayer.data

import androidx.room.Entity

/**
 * UserFollow Entity - 用户关注关联表
 *
 * 实现多用户关注隔离：
 * - 记录"谁"关注了"谁"
 * - 每个用户有独立的关注列表
 * - 支持粉丝/关注双向查询
 *
 * 复合主键：(followerId, followedId)
 * 同一用户对同一人只能关注一次
 */
@Entity(
    tableName = "user_follows",
    primaryKeys = ["followerId", "followedId"]
)
data class UserFollowEntity(

//     粉丝ID - 谁关注的（我）

    val followerId: String,


//     被关注者ID - 关注了谁（对方）
    val followedId: String,


//     关注时间戳
    val timestamp: Long = System.currentTimeMillis()
)
