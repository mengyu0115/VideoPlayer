package com.example.videoplayer.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.videoplayer.network.NetworkConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用数据库
 *
 * Room 数据库单例，管理所有数据表
 *
 * 配置说明：
 * - entities: 包含的数据表（VideoEntity, CommentEntity, UserEntity, MessageEntity）
 * - version: 数据库版本号（升级时递增）
 * - exportSchema: 是否导出数据库架构（生产环境建议 false）
 */
@Database(
    entities = [
        VideoEntity::class,
        CommentEntity::class,
        UserEntity::class,
        MessageEntity::class,
        VideoLikeEntity::class,      //  迭代13：视频点赞关联表
        UserFollowEntity::class,     //  迭代13：用户关注关联表
        VideoFavoriteEntity::class   //  视频收藏关联表
    ],
    version = 12,  // 升级到版本12，新增收藏功能
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    companion object {
        private const val TAG = "AppDatabase"

        // 单例实例，使用 @Volatile 确保多线程可见性
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库实例（单例模式）
         *
         * 使用 Double-Check Locking 确保线程安全
         *
         * @param context 应用上下文
         * @return AppDatabase 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            // 第一次检查（无锁，性能优化）
            return INSTANCE ?: synchronized(this) {
                // 第二次检查（加锁，确保只创建一次）
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * 构建数据库实例
         *
         * 配置：
         * - 数据库名称: "video_player.db"
         * - 主线程查询禁用（强制异步操作）
         * - 破坏性迁移策略：版本升级时清空数据重建（简化开发）
         * - Callback：数据库创建时预填充数据
         *
         * @param context 应用上下文
         * @return AppDatabase 数据库实例
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "video_player.db"
            )
                // 破坏性迁移：版本升级时清空数据重建（开发阶段简化处理）
                .fallbackToDestructiveMigration()
                //  添加数据库回调，预填充数据
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
//                        Log.d(TAG, "onCreate: 数据库创建，开始预填充数据")

                        // 在后台线程预填充数据
                        CoroutineScope(Dispatchers.IO).launch {
                            populateDatabase(getInstance(context))
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
//                        Log.d(TAG, "========================================")
//                        Log.d(TAG, "onOpen: 数据库已打开，开始检查数据")
//                        Log.d(TAG, "========================================")

                        //  Bug修复：强制清空并重新填充数据（版本10专用）
                        // 确保所有用户ID从旧的user_v1改为新的user1
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val database = getInstance(context)

                                // 检查是否有旧数据（user_v1等虚拟ID）
                                val oldVideo = database.videoDao().getVideoById("v1")
//                                Log.d(TAG, "onOpen: 检查视频v1 - 存在=${oldVideo != null}")

                                if (oldVideo != null) {
//                                    Log.d(TAG, "onOpen: v1的authorId=${oldVideo.authorId}")

                                    if (oldVideo.authorId.startsWith("user_v") || oldVideo.authorId.startsWith("user_video")) {
//                                        Log.e(TAG, "========================================")
//                                        Log.e(TAG, " 检测到旧数据（authorId=${oldVideo.authorId}）")
//                                        Log.e(TAG, " 开始强制清空并重建数据库...")
//                                        Log.e(TAG, "========================================")

                                        // 清空所有表
//                                        Log.d(TAG, "清空videos表...")
                                        database.videoDao().deleteAll()

//                                        Log.d(TAG, "清空user_follows表...")
                                        database.userFollowDao().deleteAll()

//                                        Log.d(TAG, "清空video_likes表...")
                                        database.videoLikeDao().deleteAll()

//                                        Log.d(TAG, "清空comments表...")
                                        database.commentDao().deleteAll()

//                                        Log.e(TAG, "所有表已清空，开始重新填充...")

                                        // 重新填充
                                        populateDatabase(database)

//                                        Log.e(TAG, "========================================")
//                                        Log.e(TAG, " 数据库重建完成！")
//                                        Log.e(TAG, "========================================")
                                    } else {
//                                        Log.d(TAG, "onOpen:  数据正确（authorId=${oldVideo.authorId}），跳过清理")
                                    }
                                } else {
//                                    Log.d(TAG, "onOpen: 数据库为空，开始预填充数据")
                                    populateDatabase(database)
                                }
                            } catch (e: Exception) {
//                                Log.e(TAG, "onOpen:  数据检查失败", e)
                            }
                        }
                    }
                })
                .build()
        }

        /**
         * 预填充数据库
         *
         * 在数据库首次创建时调用，插入初始的测试视频
         *  不再预先创建用户，用户通过注册功能创建
         *
         * @param database 数据库实例
         */
        private suspend fun populateDatabase(database: AppDatabase) {
//            Log.d(TAG, "populateDatabase: 开始预填充数据")

            val videoDao = database.videoDao()

            // ========== 定义已知用户列表（通过注册系统创建的用户） ==========
            // 这些用户ID对应服务器端已注册的账号
            val knownUsers = listOf("user1", "user2", "admin", "123456")

            // ========== 创建测试视频并永久绑定到已知用户 ==========
            val videos = listOf(
                VideoEntity(
                    id = "v1",
                    title = "服务器测试视频1",
                    videoUrl = NetworkConfig.getVideoUrl("v1.mp4"),
                    coverUrl = NetworkConfig.getCoverUrl("v1"),
                    authorId = "user1",  // 绑定到 user1
                    authorName = "user1",  // 用户名
                    authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl("user1"),
                    description = "这是来自Tomcat服务器的第一个测试视频 #服务器测试 #视频1",
                    likeCount = (0..100).random(),// 随机点赞数
                    commentCount = (0..50).random()// 随机评论数
                ),
                VideoEntity(
                    id = "v2",
                    title = "服务器测试视频2",
                    videoUrl = NetworkConfig.getVideoUrl("v2.mp4"),
                    coverUrl = NetworkConfig.getCoverUrl("v2"),
                    authorId = "user2",  //  绑定到 user2
                    authorName = "user2",
                    authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl("user2"),
                    description = "Tomcat服务器稳定运行中 #服务器测试 #视频2",
                    likeCount = (0..100).random(),
                    commentCount = (0..50).random()
                ),
                VideoEntity(
                    id = "v3",
                    title = "创意视频 A",
                    videoUrl = NetworkConfig.getVideoUrl("v3.mp4"),
                    coverUrl = NetworkConfig.getCoverUrl("v3"),
                    authorId = "admin",  // 绑定到 admin
                    authorName = "admin",
                    authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl("admin"),
                    description = "创意工坊出品 - 精彩内容A #创意 #原创",
                    likeCount = (0..100).random(),
                    commentCount = (0..50).random()
                ),
                VideoEntity(
                    id = "v4",
                    title = "创意视频 B",
                    videoUrl = NetworkConfig.getVideoUrl("v4.mp4"),
                    coverUrl = NetworkConfig.getCoverUrl("v4"),
                    authorId = "123456",  //  永久绑定到 123456
                    authorName = "123456",
                    authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl("123456"),
                    description = "创意工坊出品 - 精彩内容B #创意 #设计",
                    likeCount = (0..100).random(),
                    commentCount = (0..50).random()
                ),
                VideoEntity(
                    id = "v5",
                    title = "个人作品",
                    videoUrl = NetworkConfig.getVideoUrl("v5.mp4"),
                    coverUrl = NetworkConfig.getCoverUrl("v5"),
                    authorId = "user1",  // 绑定到 user1
                    authorName = "user1",
                    authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl("user1"),
                    description = "这是我的第一个作品，请多多支持 #我的作品 #原创",
                    likeCount = (0..100).random(),
                    commentCount = (0..50).random()
                )
            )

            videos.forEach { video ->
                videoDao.insertVideo(video)
//                Log.d(TAG, "populateDatabase: 插入视频 - ${video.title} (${video.id})")
//                Log.d(TAG, "  -> authorId: \"${video.authorId}\"（已永久绑定）")
            }

            Log.d(TAG, "populateDatabase: 数据预填充完成！")
            Log.d(TAG, "  -> 插入了 ${videos.size} 个视频（已绑定到 ${knownUsers.joinToString(", ")}）")
        }

        /**
         * 清空数据库实例（用于测试）
         *
         * 仅在测试或特殊场景下使用
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }

    /**
     * 获取视频数据访问对象
     *
     * @return VideoDao 视频数据访问接口
     */
    abstract fun videoDao(): VideoDao

    /**
     * 获取评论数据访问对象
     *
     * @return CommentDao 评论数据访问接口
     */
    abstract fun commentDao(): CommentDao

    /**
     * 获取关注用户数据访问对象
     *
     * @return UserDao 关注用户数据访问接口
     */
    abstract fun userDao(): UserDao

    /**
     * 获取消息数据访问对象
     *
     * @return MessageDao 消息数据访问接口
     */
    abstract fun messageDao(): MessageDao

    /**
     * 获取视频点赞数据访问对象（迭代13）
     *
     * @return VideoLikeDao 视频点赞数据访问接口
     */
    abstract fun videoLikeDao(): VideoLikeDao

    /**
     * 获取用户关注数据访问对象（迭代13）
     *
     * @return UserFollowDao 用户关注数据访问接口
     */
    abstract fun userFollowDao(): UserFollowDao

    /**
     * 获取视频收藏数据访问对象
     *
     * @return VideoFavoriteDao 视频收藏数据访问接口
     */
    abstract fun videoFavoriteDao(): VideoFavoriteDao
}
