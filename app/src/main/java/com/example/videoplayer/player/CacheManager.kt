package com.example.videoplayer.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.videoplayer.network.OkHttpManager
import java.io.File

/**
 * 缓存管理器 - 负责 ExoPlayer 的本地缓存配置
 *
 * 核心功能：
 * 1. 使用 SimpleCache 实现 LRU 缓存策略
 * 2. 最大缓存空间 500MB
 * 3. 所有网络请求经过 CacheDataSource
 * 4. 自动淘汰最少使用的缓存
 */
@UnstableApi
object CacheManager {

    private const val TAG = "CacheManager"

    // 缓存配置
    private const val MAX_CACHE_SIZE: Long = 500 * 1024 * 1024 // 500MB
    private const val CACHE_DIR_NAME = "video_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null

    /**
     * 初始化缓存系统
     * 必须在 Application.onCreate() 或使用前调用
     */
    @OptIn(UnstableApi::class)
    @Synchronized
    fun initialize(context: Context) {
        if (simpleCache != null) {
            Log.d(TAG, "initialize: 缓存系统已初始化，跳过")
            return
        }

        Log.d(TAG, "========== 初始化缓存系统 ==========")
        Log.d(TAG, "initialize: 最大缓存空间 = ${MAX_CACHE_SIZE / 1024 / 1024}MB")
        Log.d(TAG, "initialize: 缓存策略 = LRU (Least Recently Used)")

        try {
            // 创建缓存目录
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            Log.d(TAG, "initialize: 缓存目录 = ${cacheDir.absolutePath}")

            // 创建 LRU 缓存淘汰器
            val cacheEvictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            Log.d(TAG, "initialize:  LRU 淘汰器已创建")

            // 创建数据库提供者（用于持久化缓存元数据）
            val databaseProvider = StandaloneDatabaseProvider(context)
            Log.d(TAG, "initialize:  数据库提供者已创建")

            // 创建 SimpleCache 实例
            simpleCache = SimpleCache(cacheDir, cacheEvictor, databaseProvider)
            Log.d(TAG, "initialize:  SimpleCache 已创建")

            // 构建 CacheDataSource.Factory
            buildCacheDataSourceFactory(context)

            Log.d(TAG, "initialize: ========== 缓存系统初始化完成 ==========")

        } catch (e: Exception) {
            Log.e(TAG, "initialize:  缓存系统初始化失败", e)
            simpleCache = null
            cacheDataSourceFactory = null
        }
    }

    /**
     * 构建 CacheDataSource.Factory
     * 所有网络请求都会经过这个 Factory 创建的 DataSource
     */
    private fun buildCacheDataSourceFactory(context: Context) {
        Log.d(TAG, "buildCacheDataSourceFactory: 开始构建 CacheDataSource.Factory")

        val cache = simpleCache ?: run {
            Log.e(TAG, "buildCacheDataSourceFactory:  SimpleCache 为空，无法构建")
            return
        }

        // 初始化 OkHttp（如果还没初始化）
        OkHttpManager.initialize(context)

        // 获取 OkHttp DataSource.Factory（支持 HTTP/2）
        val okHttpDataSourceFactory = OkHttpManager.getOkHttpDataSourceFactory()
        Log.d(TAG, "buildCacheDataSourceFactory:  OkHttp DataSource.Factory 已获取（支持 HTTP/2）")

        // 创建 Default DataSource.Factory（支持多种协议：http/https/file/asset/content）
        val upstreamDataSourceFactory = DefaultDataSource.Factory(
            context,
            okHttpDataSourceFactory  // 使用 OkHttp 替代默认 HTTP
        )

        Log.d(TAG, "buildCacheDataSourceFactory:  Default DataSource.Factory 已创建")

        // 创建 CacheDataSource.Factory（核心：网络请求 + 缓存层）
        cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // 使用默认写入策略
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR) // 缓存错误时回退到网络

        Log.d(TAG, "buildCacheDataSourceFactory:  CacheDataSource.Factory 已创建")
//        Log.d(TAG, "buildCacheDataSourceFactory: 配置说明：")
//        Log.d(TAG, "  - 所有网络请求会先查询缓存")
//        Log.d(TAG, "  - 缓存未命中时从网络下载并写入缓存")
//        Log.d(TAG, "  - 缓存错误时自动回退到网络请求")
//        Log.d(TAG, "  - LRU 策略自动淘汰旧数据")
    }

    /**
     * 获取 CacheDataSource.Factory
     * 用于 ExoPlayer 和 PreloadManager
     */
    fun getCacheDataSourceFactory(): CacheDataSource.Factory {
        val factory = cacheDataSourceFactory
        if (factory == null) {
            Log.e(TAG, "getCacheDataSourceFactory:  Factory 未初始化，请先调用 initialize()")
            throw IllegalStateException("CacheManager 未初始化，请在 Application.onCreate() 中调用 initialize()")
        }
        return factory
    }

    /**
     * 获取 SimpleCache 实例
     * 用于 PreloadManager 的 CacheWriter
     */
    fun getCache(): SimpleCache {
        val cache = simpleCache
        if (cache == null) {
            Log.e(TAG, "getCache:  Cache 未初始化，请先调用 initialize()")
            throw IllegalStateException("CacheManager 未初始化，请在 Application.onCreate() 中调用 initialize()")
        }
        return cache
    }

    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        val cache = simpleCache ?: return CacheStats(0, 0, 0f)

        val cacheSpace = cache.cacheSpace
        val usedPercentage = (cacheSpace.toFloat() / MAX_CACHE_SIZE) * 100

        Log.d(TAG, "getCacheStats: 已用空间 = ${cacheSpace / 1024 / 1024}MB / ${MAX_CACHE_SIZE / 1024 / 1024}MB (${String.format("%.2f", usedPercentage)}%)")

        return CacheStats(
            usedBytes = cacheSpace,
            maxBytes = MAX_CACHE_SIZE,
            usedPercentage = usedPercentage
        )
    }

    /**
     * 清空所有缓存
     * 谨慎使用：会删除所有已缓存的视频数据
     */
    fun clearCache() {
        Log.d(TAG, "clearCache:  开始清空所有缓存")
        try {
            simpleCache?.release()
            simpleCache = null
            cacheDataSourceFactory = null
            Log.d(TAG, "clearCache:  缓存已清空")
        } catch (e: Exception) {
            Log.e(TAG, "clearCache:  清空缓存失败", e)
        }
    }

    /**
     * 缓存统计数据类
     */
    data class CacheStats(
        val usedBytes: Long,
        val maxBytes: Long,
        val usedPercentage: Float
    )
}
