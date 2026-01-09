package com.example.videoplayer.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 会话管理器 - 迭代13
 *
 * 管理当前登录用户的会话信息：
 * - 存储当前用户ID
 * - 提供全局访问接口
 * - 持久化存储（SharedPreferences）
 *
 * 使用场景：
 * - 查询点赞/关注状态时需要当前用户ID
 * - 发布视频/评论时需要作者ID
 * - 多用户切换时更新会话
 */
class SessionManager private constructor(context: Context) {

    companion object {
        private const val TAG = "SessionManager"
        private const val PREF_NAME = "session_prefs"
        private const val KEY_CURRENT_USER_ID = "current_user_id"
        private const val DEFAULT_USER_ID = "u_me"  // 默认用户ID

        @Volatile
        private var instance: SessionManager? = null

        /**
         * 获取 SessionManager 单例
         *
         * @param context 应用上下文
         * @return SessionManager 实例
         */
        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also {
                    instance = it
                    Log.d(TAG, "getInstance: SessionManager 创建")
                }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 当前登录用户ID
     *
     * 从 SharedPreferences 读取，如果不存在则返回默认值
     */
    val currentUserId: String
        get() {
            val userId = sharedPreferences.getString(KEY_CURRENT_USER_ID, DEFAULT_USER_ID) ?: DEFAULT_USER_ID
            Log.v(TAG, "currentUserId: $userId")
            return userId
        } //get()为动态属性，get()方法返回当前值

    /**
     * 设置当前登录用户ID
     *
     * @param userId 用户ID
     */
    fun setCurrentUserId(userId: String) {
        Log.d(TAG, "setCurrentUserId: $userId")
        sharedPreferences.edit()
            .putString(KEY_CURRENT_USER_ID, userId)
            .apply()
    }

    /**
     * 退出登录
     *
     * 清除当前用户ID，恢复为默认值
     */
    fun logout() {
        Log.d(TAG, "logout: 清除会话")
        sharedPreferences.edit()
            .putString(KEY_CURRENT_USER_ID, DEFAULT_USER_ID)
            .apply()
    }

    /**
     * 检查是否已登录
     *
     * @return Boolean 是否已登录（非默认用户）
     */
    fun isLoggedIn(): Boolean {
        val isLoggedIn = currentUserId != DEFAULT_USER_ID
        Log.v(TAG, "isLoggedIn: $isLoggedIn")
        return isLoggedIn
    }
}
