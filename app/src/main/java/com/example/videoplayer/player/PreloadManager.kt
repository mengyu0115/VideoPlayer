package com.example.videoplayer.player

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 预加载管理器 - 负责智能预加载下一个视频
 *
 * 核心功能：
 * 1. 当播放第 N 个视频时，自动预加载第 N+1 个视频
 * 2. 只预加载前 1MB 数据，避免浪费流量
 * 3. 使用协程在后台执行预加载
 * 4. 支持取消预加载任务
 */
object PreloadManager {

    private const val TAG = "PreloadManager"

    // 预加载配置
    private const val PRELOAD_SIZE: Long = 1 * 1024 * 1024 // 1MB

    // 协程作用域
    private val preloadScope = CoroutineScope(Dispatchers.IO)

    // 预加载任务映射表（videoUrl -> Job）
    private val preloadJobs = ConcurrentHashMap<String, Job>()

    // 已预加载的视频 URL 集合
    private val preloadedUrls = ConcurrentHashMap.newKeySet<String>()

    /**
     * 预加载指定视频的前 1MB 数据
     *
     * @param videoUrl 视频 URL
     * @param position 视频在列表中的位置（仅用于日志）
     */
    fun preload(videoUrl: String, position: Int) {
        Log.d(TAG, "========== 预加载请求 ==========")
        Log.d(TAG, "preload: 视频位置 = $position")
        Log.d(TAG, "preload: 视频 URL = $videoUrl")

        // 检查是否已经预加载过
        if (preloadedUrls.contains(videoUrl)) {
            Log.d(TAG, "preload:  该视频已预加载，跳过")
            return
        }

        // 检查是否正在预加载
        if (preloadJobs.containsKey(videoUrl)) {
            Log.d(TAG, "preload:  该视频正在预加载中，跳过")
            return
        }

        // 检查是否为本地资源（android.resource:// 开头的不需要预加载）
        if (videoUrl.startsWith("android.resource://")) {
            Log.d(TAG, "preload:  本地资源视频，无需预加载，跳过")
            preloadedUrls.add(videoUrl) // 标记为已处理，避免重复检查
            return
        }

        Log.d(TAG, "preload:  开始预加载前 ${PRELOAD_SIZE / 1024}KB 数据")

        // 启动协程执行预加载
        val job = preloadScope.launch {
            try {
                performPreload(videoUrl, position)
            } catch (e: Exception) {
                Log.e(TAG, "preload:  预加载失败 (position=$position)", e)
                // 预加载失败不影响正常播放，用户播放时会从网络加载
            } finally {
                // 清理任务记录
                preloadJobs.remove(videoUrl)
            }
        }

        // 记录任务
        preloadJobs[videoUrl] = job
        Log.d(TAG, "preload: 预加载任务已启动 (jobId=${job.hashCode()})")
    }

    /**
     * 执行预加载（在 IO 线程执行）
     */
    @OptIn(UnstableApi::class)
    private suspend fun performPreload(videoUrl: String, position: Int) = withContext(Dispatchers.IO) {
        Log.d(TAG, "performPreload: [协程] 开始执行预加载 (position=$position)")
        Log.d(TAG, "performPreload: [协程] 线程 = ${Thread.currentThread().name}")

        val startTime = System.currentTimeMillis()

        try {
            // 获取 Cache 和 DataSource.Factory
            val cache = CacheManager.getCache()
            val dataSourceFactory = CacheManager.getCacheDataSourceFactory()

            Log.d(TAG, "performPreload: [协程] 准备 CacheWriter")
            Log.d(TAG, "performPreload: [协程] 目标 URL = $videoUrl")
            Log.d(TAG, "performPreload: [协程] 预加载大小 = ${PRELOAD_SIZE / 1024}KB")

            // 构建 DataSpec（指定只下载前 1MB）
            val dataSpec = DataSpec.Builder()
                .setUri(Uri.parse(videoUrl))
                .setPosition(0) // 从头开始
                .setLength(PRELOAD_SIZE) // 只下载 1MB
                .build()

            Log.d(TAG, "performPreload: [协程] DataSpec 已构建")
            Log.d(TAG, "performPreload: [协程]   - position = ${dataSpec.position}")
            Log.d(TAG, "performPreload: [协程]   - length = ${dataSpec.length} bytes")

            // 创建 CacheWriter（负责从网络下载并写入缓存）
            val cacheWriter = CacheWriter(
                dataSourceFactory.createDataSource(),
                dataSpec,
                null, // 不需要临时缓存
                null  // 不需要进度监听
            )

            Log.d(TAG, "performPreload: [协程] CacheWriter 已创建，开始写入缓存...")

            // 执行缓存写入（阻塞操作，直到下载完成）
            cacheWriter.cache()

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "performPreload: [协程] 预加载完成！")
            Log.d(TAG, "performPreload: [协程] 耗时 = ${duration}ms")
            Log.d(TAG, "performPreload: [协程] 已缓存前 ${PRELOAD_SIZE / 1024}KB 数据")

            // 标记为已预加载
            preloadedUrls.add(videoUrl)

            Log.d(TAG, "performPreload: [协程] ========== 预加载成功 ==========")

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "performPreload: [协程] 预加载异常 (耗时=${duration}ms)", e)
            Log.e(TAG, "performPreload: [协程] 错误类型 = ${e.javaClass.simpleName}")
            Log.e(TAG, "performPreload: [协程] 错误信息 = ${e.message}")
            throw e
        }
    }

    /**
     * 取消指定视频的预加载任务
     */
    fun cancelPreload(videoUrl: String) {
        Log.d(TAG, "cancelPreload: 取消预加载 URL=$videoUrl")
        val job = preloadJobs.remove(videoUrl)
        if (job != null) {
            job.cancel()
            Log.d(TAG, "cancelPreload: 已取消预加载任务 (jobId=${job.hashCode()})")
        } else {
            Log.d(TAG, "cancelPreload: 无预加载任务需要取消")
        }
    }

    /**
     * 取消所有预加载任务
     */
    fun cancelAllPreloads() {
        Log.d(TAG, "cancelAllPreloads: 取消所有预加载任务")
        val count = preloadJobs.size
        preloadJobs.values.forEach { it.cancel() }
        preloadJobs.clear()
        Log.d(TAG, "cancelAllPreloads: 已取消 $count 个预加载任务")
    }

    /**
     * 获取预加载统计信息
     */
    fun getStats(): PreloadStats {
        val activeCount = preloadJobs.size
        val completedCount = preloadedUrls.size

        Log.d(TAG, "getStats: 活跃预加载任务 = $activeCount")
        Log.d(TAG, "getStats: 已完成预加载 = $completedCount")

        return PreloadStats(
            activePreloadCount = activeCount,
            completedPreloadCount = completedCount
        )
    }

    /**
     * 清空预加载记录（用于测试或重置）
     */
    fun clearStats() {
        Log.d(TAG, "clearStats: 清空预加载记录")
        cancelAllPreloads()
        preloadedUrls.clear()
        Log.d(TAG, "clearStats: 预加载记录已清空")
    }

    /**
     * 预加载统计数据类
     */
    data class PreloadStats(
        val activePreloadCount: Int,
        val completedPreloadCount: Int
    )
}
