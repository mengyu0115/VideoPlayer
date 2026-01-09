package com.example.videoplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 关注用户数据访问对象（DAO）
 *
 * 提供关注用户相关的数据库操作接口
 */
@Dao
interface UserDao {

    /**
     * 获取所有关注的用户
     *
     * 返回 Flow，当关注列表变化时，UI 自动刷新
     * 按关注时间倒序排列（最新关注在前）
     *
     * @return Flow<List<UserEntity>> 关注用户列表的 Flow 流
     */
    @Query("SELECT * FROM followed_users WHERE isFollowing = 1 ORDER BY followedAt DESC")
    fun getAllFollowedUsers(): Flow<List<UserEntity>>

    /**
     * 插入关注的用户
     *
     * 如果 userId 冲突，则替换旧记录
     *
     * @param user 要插入的用户
     */
    //onConflict = OnConflictStrategy.REPLACE指示如果插入的记录的userId已经存在，则替换该记录
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    /**
     * 批量插入用户
     *
     * 用于初始化测试数据
     *
     * @param users 要插入的用户列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    /**
     * 根据 userId 删除用户
     *
     * 用于取消关注
     *
     * @param userId 用户 ID
     */
    @Query("DELETE FROM followed_users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    /**
     * 根据 userId 查询用户
     *
     * 用于检查是否已关注
     *
     * @param userId 用户 ID
     * @return UserEntity? 用户实体，不存在则返回 null
     */
    @Query("SELECT * FROM followed_users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    /**
     * 检查是否已关注某用户
     *
     * @param userId 用户 ID
     * @return Boolean true 表示已关注，false 表示未关注
     */
    @Query("SELECT EXISTS(SELECT 1 FROM followed_users WHERE userId = :userId AND isFollowing = 1)")
    suspend fun isFollowing(userId: String): Boolean

    /**
     * 获取关注用户数量
     *
     * @return Int 关注数
     */
    @Query("SELECT COUNT(*) FROM followed_users WHERE isFollowing = 1")
    suspend fun getFollowedUserCount(): Int

    /**
     * 删除所有关注用户
     *
     * 用于清空数据或测试
     */
    @Query("DELETE FROM followed_users")
    suspend fun deleteAllUsers()
}
