package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * UserFollow DAO - 用户关注数据访问对象
 *
 * 管理用户之间的关注关系：
 * - 查询关注状态
 * - 添加/取消关注
 * - 获取关注/粉丝列表
 */
@Dao
interface UserFollowDao {

    /**
     * 检查用户A是否关注了用户B
     *
     * @param followerId 粉丝ID（关注者）
     * @param followedId 被关注者ID
     * @return Boolean 是否已关注
     */
    @Query("SELECT EXISTS(SELECT 1 FROM user_follows WHERE followerId = :followerId AND followedId = :followedId)")
    suspend fun isFollowing(followerId: String, followedId: String): Boolean

    /**
     * 添加关注
     *
     * 如果已存在（复合主键冲突），则忽略
     *
     * @param follow 关注记录
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFollow(follow: UserFollowEntity)

    /**
     * 取消关注
     *
     * @param followerId 粉丝ID（关注者）
     * @param followedId 被关注者ID
     */
    @Query("DELETE FROM user_follows WHERE followerId = :followerId AND followedId = :followedId")
    suspend fun deleteFollow(followerId: String, followedId: String)

    /**
     * 获取用户关注的所有人的ID列表
     *
     * 返回 Flow，当关注数据变化时，UI 自动刷新
     *
     * @param followerId 粉丝ID（我的ID）
     * @return Flow<List<String>> 被关注者ID列表的 Flow 流
     */
    @Query("SELECT followedId FROM user_follows WHERE followerId = :followerId ORDER BY timestamp DESC")
    fun getFollowing(followerId: String): Flow<List<String>>

    /**
     * 获取用户的粉丝ID列表
     *
     * 返回 Flow，当粉丝数据变化时，UI 自动刷新
     *
     * @param followedId 被关注者ID（我的ID）
     * @return Flow<List<String>> 粉丝ID列表的 Flow 流
     */
    @Query("SELECT followerId FROM user_follows WHERE followedId = :followedId ORDER BY timestamp DESC")
    fun getFollowers(followedId: String): Flow<List<String>>

    /**
     * 获取用户关注的人数
     *
     * @param followerId 粉丝ID（我的ID）
     * @return Int 关注数
     */
    @Query("SELECT COUNT(*) FROM user_follows WHERE followerId = :followerId")
    suspend fun getFollowingCount(followerId: String): Int

    /**
     * 获取用户的粉丝数
     *
     * @param followedId 被关注者ID（我的ID）
     * @return Int 粉丝数
     */
    @Query("SELECT COUNT(*) FROM user_follows WHERE followedId = :followedId")
    suspend fun getFollowerCount(followedId: String): Int

    /**
     * 删除所有关注记录
     *
     * 用于清空数据或测试
     */
    @Query("DELETE FROM user_follows")
    suspend fun deleteAllFollows()

    /**
     * 删除所有关注记录（别名方法）
     */
    suspend fun deleteAll() = deleteAllFollows()
}
