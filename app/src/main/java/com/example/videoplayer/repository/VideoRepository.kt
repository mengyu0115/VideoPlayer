package com.example.videoplayer.repository

import android.content.Context
import android.util.Log
import com.example.videoplayer.R
import com.example.videoplayer.data.AppDatabase
import com.example.videoplayer.data.CommentDao
import com.example.videoplayer.data.CommentEntity
import com.example.videoplayer.data.UserDao
import com.example.videoplayer.data.UserEntity
import com.example.videoplayer.data.VideoDao
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.data.VideoLikeDao
import com.example.videoplayer.data.VideoLikeEntity
import com.example.videoplayer.data.UserFollowDao
import com.example.videoplayer.utils.SessionManager
import com.example.videoplayer.network.SocketManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * 视频数据仓库层（Repository Pattern）- 迭代13重构
 *
 * 职责：
 * - 协调数据库和网络数据源
 * - 实现 Single Source of Truth（SSOT）
 * - 管理数据缓存和刷新策略
 * - 处理社交互动（点赞、评论、关注）
 *
 *  迭代13架构变更：
 * - 运行时组装用户状态（isLiked、isFavorite）
 * - 通过关联表实现多用户数据隔离
 * - 使用 SessionManager 获取当前用户ID
 *
 * 架构：
 * - 数据库作为唯一可信数据源
 * - 网络数据写入数据库后自动通知 UI
 * - UI 只观察数据库 Flow，不直接访问网络
 */
class VideoRepository(context: Context) {

    companion object {
        private const val TAG = "VideoRepository"
    }

    private val database = AppDatabase.getInstance(context)
    private val videoDao: VideoDao = database.videoDao()
    private val commentDao: CommentDao = database.commentDao()
    private val userDao: UserDao = database.userDao()
    private val videoLikeDao: VideoLikeDao = database.videoLikeDao()
    private val userFollowDao: UserFollowDao = database.userFollowDao()
    private val messageDao = database.messageDao()
    private val sessionManager = SessionManager.getInstance(context)

    /**
     * 所有视频的 Flow 流（迭代13重构）
     *
     * 这是 SSOT 的核心：
     * - UI 只观察这个 Flow
     * - 数据库变化时自动推送新数据
     * - 运行时为每个视频组装用户状态（isLiked、isFavorite）
     */
    val allVideos: Flow<List<VideoEntity>> = videoDao.getAllVideos()
        .map { videos ->
            Log.d(TAG, "allVideos.map: 收到 ${videos.size} 个视频，开始组装用户状态")
            attachUserStates(videos)//运行时为每个视频组装用户状态（isLiked、isFavorite）
        }

    /**
     * 为视频列表附加当前用户的交互状态（迭代13核心方法）
     *
     * 实现逻辑：
     * 1. 获取当前用户ID
     * 2. 对每个视频，查询该用户是否点赞/收藏
     * 3. 计算该视频是否是当前用户发布的（isMine）
     * 4. 使用 copy() 创建新的 VideoEntity 实例，填充状态
     *
     * @param videos 纯视频列表（不含用户状态）
     * @return List<VideoEntity> 包含用户状态的视频列表
     */
    private suspend fun attachUserStates(videos: List<VideoEntity>): List<VideoEntity> {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "attachUserStates: 当前用户ID=$currentUserId，处理 ${videos.size} 个视频")

        return videos.map { video ->
            // 查询当前用户是否点赞了这个视频
            val isLiked = videoLikeDao.isVideoLiked(currentUserId, video.id)

            // 收藏功能暂时复用点赞表（后续可扩展为独立的 VideoFavorite 表）
            val isFavorite = video.isFavorite  // 暂时保留原值

            //  计算该视频是否是当前用户发布的（多用户数据隔离）
            val isMine = (video.authorId == currentUserId)

            Log.v(TAG, "attachUserStates: video=${video.id}, authorId=${video.authorId}, isLiked=$isLiked, isMine=$isMine")

            // 使用 copy() 创建新实例，填充用户状态
            video.copy(
                isLiked = isLiked,
                isFavorite = isFavorite,
                isMine = isMine  //  运行时计算的 isMine 状态
            )
        }
    }

    /**
     * 刷新视频列表
     *
     * 模拟从网络获取数据，然后写入数据库
     * 实际项目中，这里应该调用网络 API
     *
     * 执行流程：
     * 1. 生成测试数据（模拟网络请求）
     * 2. 只插入数据库中不存在的视频（保留用户的点赞、评论等交互数据）
     * 3. Flow 自动通知 UI 刷新
     */
    suspend fun refreshVideos() {
        Log.d(TAG, "refreshVideos: 开始刷新视频列表（模拟网络请求）")

        // 生成测试视频数据
        val testVideos = generateTestVideos()
        Log.d(TAG, "refreshVideos: 生成了 ${testVideos.size} 个测试视频")

        // 只插入数据库中不存在的视频，避免覆盖用户的点赞、评论等交互数据
        var insertedCount = 0
        testVideos.forEach { video ->
            val existingVideo = videoDao.getVideoById(video.id)
            if (existingVideo == null) {
                // 视频不存在，插入新视频
                videoDao.insertVideo(video)
                insertedCount++
                Log.d(TAG, "refreshVideos: 插入新视频 - id=${video.id}")
            } else {
                // 视频已存在，保留用户数据，不覆盖
                Log.d(TAG, "refreshVideos: 视频已存在，跳过 - id=${video.id}, isLiked=${existingVideo.isLiked}, likeCount=${existingVideo.likeCount}")
            }
        }
        Log.d(TAG, "refreshVideos:  插入了 $insertedCount 个新视频，已保留用户交互数据")
    }

    /**
     * 从服务器同步视频列表（迭代15）
     *
     * 功能：
     * 1. 发送 GET_VIDEOS 请求到服务器
     * 2. 监听 videoListFlow 接收视频列表响应
     * 3. 解析 JSONObject 并转换为 VideoEntity
     * 4. 插入数据库（避免重复）
     *
     * 这解决了重装应用后视频消失的问题
     */
    suspend fun syncVideosFromServer() {
        Log.d(TAG, "syncVideosFromServer: 开始从服务器同步视频")

        try {
            // 发送 GET_VIDEOS 请求
            SocketManager.getVideos()
            Log.d(TAG, "syncVideosFromServer: 已发送 GET_VIDEOS 请求，等待响应...")

            // 监听 videoListFlow，设置5秒超时
            val videoJsonList = withTimeout(5000) {
                SocketManager.videoListFlow.first()
            }

            Log.d(TAG, "syncVideosFromServer: 收到 ${videoJsonList.size} 个视频")

            // 转换 JSONObject 为 VideoEntity 并插入数据库
            var syncedCount = 0
            videoJsonList.forEach { videoJson ->
                try {
                    val videoEntity = parseVideoJson(videoJson)

                    // 检查数据库中是否已存在该视频
                    val existingVideo = videoDao.getVideoById(videoEntity.id)
                    if (existingVideo == null) {
                        // 视频不存在，插入新视频
                        videoDao.insertVideo(videoEntity)
                        syncedCount++
                        Log.d(TAG, "syncVideosFromServer:  同步新视频 - id=${videoEntity.id}, title=${videoEntity.title}")
                    } else {
                        Log.d(TAG, "syncVideosFromServer: 视频已存在，跳过 - id=${videoEntity.id}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "syncVideosFromServer:  解析视频失败", e)
                }
            }

            Log.d(TAG, "syncVideosFromServer:  同步完成，新增 $syncedCount 个视频")

        } catch (e: Exception) {
            Log.e(TAG, "syncVideosFromServer:  同步失败", e)
            // 不抛出异常，允许应用继续运行（使用本地数据）
        }
    }

    /**
     * 解析 JSONObject 为 VideoEntity
     */
    private fun parseVideoJson(json: JSONObject): VideoEntity {
        return VideoEntity(
            id = json.getString("id"),
            title = json.getString("title"),
            videoUrl = json.getString("videoUrl"),
            coverUrl = json.getString("coverUrl"),
            authorId = json.getString("authorId"),
            authorName = json.getString("authorName"),
            authorAvatarUrl = json.getString("authorAvatarUrl"),
            description = json.getString("description"),
            likeCount = json.getInt("likeCount"),
            commentCount = json.getInt("commentCount"),
            isLiked = false,  // 初始状态，由 attachUserStates 计算
            isFavorite = false,  // 初始状态
            isMine = false  // 初始状态，由 attachUserStates 计算
        )
    }

    /**
     * 切换点赞状态（迭代13重构）
     *
     * 架构变更：
     * - 不再直接更新 VideoEntity 的 isLiked 字段
     * - 操作 video_likes 关联表
     * - 实现多用户数据隔离
     *
     * @param videoId 视频 ID
     * @param isLiked 是否已点赞
     */
    suspend fun toggleLike(videoId: String, isLiked: Boolean) {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "toggleLike: userId=$currentUserId, videoId=$videoId, isLiked=$isLiked")

        if (isLiked) {
            // 添加点赞记录
            val likeEntity = VideoLikeEntity(
                userId = currentUserId,
                videoId = videoId,
                timestamp = System.currentTimeMillis()
            )
            videoLikeDao.insertLike(likeEntity)
            Log.d(TAG, "toggleLike:  添加点赞记录")
        } else {
            // 删除点赞记录
            videoLikeDao.deleteLike(currentUserId, videoId)
            Log.d(TAG, "toggleLike:  删除点赞记录")
        }

        // 更新视频的点赞数（+1 或 -1）
        val currentVideo = videoDao.getVideoById(videoId)
        if (currentVideo != null) {
            val newLikeCount = if (isLiked) {
                currentVideo.likeCount + 1
            } else {
                (currentVideo.likeCount - 1).coerceAtLeast(0)  // 确保不小于0
            }
            videoDao.updateLikeCount(videoId, newLikeCount)
            Log.d(TAG, "toggleLike:  点赞数已更新为 $newLikeCount")
        }
    }

    /**
     * 切换收藏状态（迭代13重构）
     *
     * 架构变更：
     * - 暂时保留原有实现（使用 VideoEntity 的 isFavorite 字段）
     * - 后续可扩展为独立的 VideoFavorite 关联表
     *
     * @param videoId 视频 ID
     * @param isFavorite 是否已收藏
     */
    suspend fun toggleFavorite(videoId: String, isFavorite: Boolean) {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "toggleFavorite: userId=$currentUserId, videoId=$videoId, isFavorite=$isFavorite")

        // 暂时保留原有实现
        videoDao.updateFavoriteStatus(videoId, isFavorite)
        Log.d(TAG, "toggleFavorite:  收藏状态已更新")
    }

    /**
     * 获取所有收藏的视频
     *
     * @return Flow<List<VideoEntity>> 收藏视频列表的 Flow 流
     */
    fun getFavoriteVideos(): Flow<List<VideoEntity>> {
        Log.d(TAG, "getFavoriteVideos: 获取收藏列表")
        return videoDao.getFavoriteVideos()
    }

    /**已弃用
     * 生成测试视频数据
     *
     * 模拟网络 API 返回的数据
     * 实际项目中，这里应该调用 Retrofit/OkHttp 等网络库
     *
     * @return List<VideoEntity> 测试视频列表
     */
    private fun generateTestVideos(): List<VideoEntity> {
        Log.d(TAG, "generateTestVideos: 开始生成测试数据")

        // 定义测试视频数据（包含横屏和竖屏）
        data class TestVideo(
            val url: String,
            val orientation: String,  // "横屏" 或 "竖屏"
            val aspectRatio: String,   // 宽高比描述
            val note: String = ""      // 备注说明
        )

        val testVideoData = listOf(
            // 1. 横屏视频 (16:9) - Big Buck Bunny
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "经典测试视频"
            ),
            // 2. 竖屏视频 (9:16) - 本地资源
            TestVideo(
                url = "android.resource://com.example.videoplayer/${R.raw.portrait_video_1}",
                orientation = "竖屏",
                aspectRatio = "9:16",
                note = "本地竖屏视频"
            ),
            // 3. 横屏视频 (16:9) - Elephant Dream
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "长视频测试"
            ),
            // 4. 竖屏视频 (9:16) - 本地资源
            TestVideo(
                url = "android.resource://com.example.videoplayer/${R.raw.portrait_video_1}",
                orientation = "竖屏",
                aspectRatio = "9:16",
                note = "本地竖屏视频"
            ),
            // 5. 横屏视频 (16:9) - For Bigger Fun
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "短视频"
            ),
            // 6. 横屏视频 (16:9) - For Bigger Joyrides
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "测试视频"
            ),
            // 7. 横屏视频 (16:9) - For Bigger Meltdowns
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "测试视频"
            ),
            // 8. 横屏视频 (16:9) - Sintel
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "长片测试"
            ),
            // 9. 横屏视频 (16:9) - Subaru
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "汽车视频"
            ),
            // 10. 横屏视频 (16:9) - Tears of Steel
            TestVideo(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                orientation = "横屏",
                aspectRatio = "16:9",
                note = "科幻短片"
            )
        )

        Log.d(TAG, "generateTestVideos: 准备了 ${testVideoData.size} 个测试视频")

        //  Bug修复：使用真实的聊天用户ID作为视频作者
        val realUserIds = listOf("user1", "user2", "admin")

        val testVideos = testVideoData.mapIndexed { index, videoData ->
            // 随机分配真实用户作为作者
            val authorId = realUserIds[index % realUserIds.size]

            VideoEntity(
                id = "video_$index",  // String 类型 ID
                title = "视频 ${index + 1} [${videoData.orientation} ${videoData.aspectRatio}]",
                coverUrl = "https://picsum.photos/seed/${index}/1080/1920",
                videoUrl = videoData.url,
                authorId = authorId,  //  使用真实聊天用户ID
                authorName = authorId,  //  显示真实用户名（不带@前缀）
                authorAvatarUrl = getUserAvatarUrl(index),
                description = getDescription(index, videoData.note),
                likeCount = 0,  //  初始点赞数为 0，真实点赞才增加
                commentCount = 0,  //  初始评论数为 0，真实评论才增加
                isLiked = false,  // 默认未点赞
                isFavorite = false,  // 默认未收藏
                isMine = false  //  旧测试数据默认不是"我的"视频
            ).also { video ->
                Log.d(TAG, "generateTestVideos: 创建视频[${video.id}]")
                Log.d(TAG, "generateTestVideos:   title=${video.title}")
                Log.d(TAG, "generateTestVideos:   authorId=${video.authorId}")
                Log.d(TAG, "generateTestVideos:   authorName=${video.authorName}")
                Log.d(TAG, "generateTestVideos:   description=${video.description}")
                Log.d(TAG, "generateTestVideos:   likeCount=${video.likeCount}, commentCount=${video.commentCount}")
            }
        }

        Log.d(TAG, "generateTestVideos: ✅ 测试数据生成完成，共 ${testVideos.size} 条")
        return testVideos
    }

    /**已弃用
     * 生成用户昵称（模拟数据）
     */
    private fun getUserName(index: Int): String {
        val names = listOf(
            "@小红薯", "@抖音达人", "@视频创作者", "@美食探店",
            "@旅行日记", "@生活记录", "@科技分享", "@音乐爱好者",
            "@健身博主", "@时尚穿搭"
        )
        return names[index % names.size]
    }

    /**已弃用
     * 生成视频描述（模拟数据）
     */
    private fun getDescription(index: Int, note: String): String {
        val descriptions = listOf(
            "这是一个非常有趣的短视频，分享给大家看看！#生活记录 #日常分享",
            "今天发现了一个宝藏地方，风景真的太美了 #旅行 #风景",
            "给大家分享一个超实用的小技巧 #生活小妙招 #实用",
            "记录美好生活的每一刻 ✨ #Vlog #生活记录",
            "这个瞬间真的太治愈了，希望你们也喜欢 #治愈 #美好瞬间",
            "分享日常，感受生活 🌟 #日常 #分享",
            "今天的心情就像这个视频一样美好 #心情 #开心",
            "偶遇的小惊喜，分享给你们 #惊喜 #发现",
            "一起感受这份快乐吧 😊 #快乐 #分享",
            "用心记录，用爱分享 ❤️ #记录 #生活"
        )
        return descriptions[index % descriptions.size]
    }

    /**已弃用
     * 生成点赞数（模拟数据）
     */
    private fun getLikeCount(index: Int): Int {
        return (1000 + index * 5678) % 100000
    }

    /**已弃用
     * 生成评论数（模拟数据）
     */
    private fun getCommentCount(index: Int): Int {
        return getLikeCount(index) / 10 + (index * 123) % 1000
    }

    /**已弃用
     * 生成用户头像 URL（模拟数据）
     */
    private fun getUserAvatarUrl(index: Int): String {
        return "https://picsum.photos/seed/avatar${index}/200/200"
    }

    // ========== 社交功能：评论 ==========

    /**
     * 获取指定视频的评论列表
     *
     * @param videoId 视频 ID
     * @return Flow<List<CommentEntity>> 评论列表的 Flow 流
     */
    fun getCommentsForVideo(videoId: String): Flow<List<CommentEntity>> {
        Log.d(TAG, "getCommentsForVideo: videoId=$videoId")
        return commentDao.getCommentsForVideo(videoId)
    }

    /**
     * 发送评论
     *
     * 构造评论实体并插入数据库
     * 实际项目中，这里应该调用网络 API
     *
     * @param videoId 视频 ID
     * @param content 评论内容
     * @param userName 评论用户昵称（当前用户）
     * @param avatarUrl 评论用户头像 URL（当前用户）
     * @param userId 评论用户ID（迭代13新增）
     */
    suspend fun sendComment(
        videoId: String,
        content: String,
        userName: String = "我",  // 默认当前用户
        avatarUrl: String = "https://picsum.photos/seed/currentUser/200/200",
        userId: String = "u_me"  //  迭代13：默认当前用户ID
    ) {
        Log.d(TAG, "sendComment: videoId=$videoId, content=$content, userId=$userId")

        val comment = CommentEntity(
            videoId = videoId,
            userId = userId,  //  迭代13：添加userId字段
            content = content,
            userName = userName,
            avatarUrl = avatarUrl,
            timestamp = System.currentTimeMillis()
        )

        commentDao.insertComment(comment)
        Log.d(TAG, "sendComment:  评论已插入数据库")

        // 更新视频的评论数（+1）
        val currentVideo = videoDao.getVideoById(videoId)
        if (currentVideo != null) {
            val newCommentCount = currentVideo.commentCount + 1
            videoDao.updateCommentCount(videoId, newCommentCount)
            Log.d(TAG, "sendComment:  评论数已更新为 $newCommentCount")
        }
    }

    // ========== 社交功能：关注 ==========

    /**
     * 获取所有关注的用户（迭代13修复：多用户数据隔离）
     *
     * 架构变更：
     * 1. 使用 UserFollowDao 查询当前用户关注的用户ID列表
     * 2. 不再直接返回 UserEntity，而是返回被关注用户的详细信息
     *
     * TODO: 当前实现仅返回用户ID列表，后续需要扩展为返回完整的用户信息
     * （例如从服务器获取用户详情，或者维护一个用户信息缓存表）
     *
     * @return Flow<List<String>> 当前用户关注的用户ID列表
     */
    fun getAllFollowedUsers(): Flow<List<String>> {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "getAllFollowedUsers: 获取用户 $currentUserId 的关注列表")
        return userFollowDao.getFollowing(currentUserId)
    }

    /**
     * 切换关注状态（迭代13修复：多用户数据隔离）
     *
     * 架构变更：
     * 1. 使用 UserFollowDao 操作 user_follows 关联表
     * 2. 记录 followerId（当前用户） 和 followedId（被关注用户）
     * 3. 实现真正的多用户数据隔离
     *
     * @param userId 被关注用户的 ID
     * @param userName 被关注用户的昵称（用于 UI 显示，暂不存储）
     * @param avatarUrl 被关注用户的头像 URL（用于 UI 显示，暂不存储）
     * @return Boolean true 表示已关注，false 表示已取消关注
     */
    suspend fun toggleFollow(
        userId: String,
        userName: String,
        avatarUrl: String
    ): Boolean {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "toggleFollow: currentUser=$currentUserId, targetUser=$userId")

        // 检查当前用户是否已关注目标用户
        val isFollowing = userFollowDao.isFollowing(currentUserId, userId)

        return if (isFollowing) {
            // 已关注 → 取消关注
            userFollowDao.deleteFollow(currentUserId, userId)
            Log.d(TAG, "toggleFollow:  $currentUserId 已取消关注 $userId")
            false
        } else {
            // 未关注 → 关注
            val followEntity = com.example.videoplayer.data.UserFollowEntity(
                followerId = currentUserId,
                followedId = userId,
                timestamp = System.currentTimeMillis()
            )
            userFollowDao.insertFollow(followEntity)
            Log.d(TAG, "toggleFollow:  $currentUserId 已关注 $userId")
            true
        }
    }

    /**
     * 检查是否已关注某用户（迭代13修复：多用户数据隔离）
     *
     * 架构变更：
     * 1. 使用 UserFollowDao 查询当前用户是否关注了目标用户
     * 2. 传入 followerId 和 followedId 两个参数
     *
     * @param userId 被关注用户的 ID
     * @return Boolean true 表示已关注，false 表示未关注
     */
    suspend fun isFollowing(userId: String): Boolean {
        val currentUserId = sessionManager.currentUserId
        val result = userFollowDao.isFollowing(currentUserId, userId)
        Log.d(TAG, "isFollowing: currentUser=$currentUserId, targetUser=$userId, result=$result")
        return result
    }

    // ========== 聊天功能：消息发送者自动添加为联系人 ==========

    /**
     * 自动将发送过消息的用户添加到联系人列表
     *
     * Bug修复：当用户登录时，检查是否有其他用户发送过消息
     * 如果有，自动将这些用户添加到联系人列表（关注列表）
     *
     * 执行逻辑：
     * 1. 查询所有给当前用户发送过消息的用户ID
     * 2. 对每个发送者，检查是否已关注
     * 3. 如果未关注，自动添加到关注列表
     *
     * @return 新添加的联系人数量
     */
    suspend fun autoAddMessageSendersToContacts(): Int {
        val currentUserId = sessionManager.currentUserId
        Log.d(TAG, "autoAddMessageSendersToContacts: 开始检查用户 $currentUserId 的消息发送者")

        // 获取所有给当前用户发送过消息的用户ID
        val senders = messageDao.getMessageSenders(currentUserId)
        Log.d(TAG, "autoAddMessageSendersToContacts: 找到 ${senders.size} 个消息发送者: $senders")

        var addedCount = 0

        // 对每个发送者，检查是否已关注
        for (senderId in senders) {
            val isFollowing = userFollowDao.isFollowing(currentUserId, senderId)

            if (!isFollowing) {
                // 未关注，自动添加到关注列表
                val followEntity = com.example.videoplayer.data.UserFollowEntity(
                    followerId = currentUserId,
                    followedId = senderId,
                    timestamp = System.currentTimeMillis()
                )
                userFollowDao.insertFollow(followEntity)
                addedCount++
                Log.d(TAG, "autoAddMessageSendersToContacts:  已自动添加联系人 $senderId")
            } else {
                Log.d(TAG, "autoAddMessageSendersToContacts: 发送者 $senderId 已在联系人列表中")
            }
        }

        Log.d(TAG, "autoAddMessageSendersToContacts:  完成，新添加 $addedCount 个联系人")
        return addedCount
    }
}
