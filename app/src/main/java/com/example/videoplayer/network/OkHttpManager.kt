package com.example.videoplayer.network

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * OkHttp 网络层管理器
 *
 * 核心功能：
 * 1. 全局单例 OkHttpClient，支持 HTTP/2
 * 2. 连接池优化，复用 TCP 连接
 * 3. 合理的超时配置
 * 4. 提供 OkHttpDataSource.Factory 给 ExoPlayer
 */
object OkHttpManager {

    private const val TAG = "OkHttpManager"

    // 网络配置
    private const val CONNECT_TIMEOUT = 15L // 连接超时 15 秒
    private const val READ_TIMEOUT = 15L     // 读取超时 15 秒
    private const val WRITE_TIMEOUT = 15L    // 写入超时 15 秒

    // 连接池配置
    private const val MAX_IDLE_CONNECTIONS = 5  // 最大空闲连接数
    private const val KEEP_ALIVE_DURATION = 5L  // 连接保活时长（分钟）
    //@Volatile用于线程安全
    @Volatile
    private var okHttpClient: OkHttpClient? = null

    @Volatile
    private var okHttpDataSourceFactory: OkHttpDataSource.Factory? = null

    /**
     * 初始化 OkHttp 客户端
     */
    @Synchronized
    fun initialize(context: Context) {
        if (okHttpClient != null) {
            Log.d(TAG, "initialize: OkHttp 已初始化，跳过")
            return
        }

        Log.d(TAG, "========== 初始化 OkHttp 网络层 ==========")
        Log.d(TAG, "initialize: 配置 HTTP/2 支持")
        Log.d(TAG, "initialize: 连接超时 = ${CONNECT_TIMEOUT}s")
        Log.d(TAG, "initialize: 读取超时 = ${READ_TIMEOUT}s")

        // 创建连接池
        val connectionPool = ConnectionPool(
            maxIdleConnections = MAX_IDLE_CONNECTIONS,
            keepAliveDuration = KEEP_ALIVE_DURATION,
            timeUnit = TimeUnit.MINUTES
        )
        Log.d(TAG, "initialize: 连接池配置 - 最大空闲连接=${MAX_IDLE_CONNECTIONS}, 保活时长=${KEEP_ALIVE_DURATION}分钟")

        // 创建 OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)) // 优先使用 HTTP/2
            .retryOnConnectionFailure(true) // 连接失败时重试
            .followRedirects(true)          // 自动跟随重定向
            .build()

        Log.d(TAG, "initialize:  OkHttpClient 已创建")
        Log.d(TAG, "initialize: 支持协议 = ${okHttpClient!!.protocols}")

        // 创建 OkHttpDataSource.Factory
        buildOkHttpDataSourceFactory()

        Log.d(TAG, "initialize: ========== OkHttp 网络层初始化完成 ==========")
    }

    /**
     * 构建 OkHttpDataSource.Factory
     */
    @OptIn(UnstableApi::class)
    private fun buildOkHttpDataSourceFactory() {
        Log.d(TAG, "buildOkHttpDataSourceFactory: 开始构建 OkHttpDataSource.Factory")

        val client = okHttpClient ?: run {
            Log.e(TAG, "buildOkHttpDataSourceFactory:  OkHttpClient 为空，无法构建")
            return
        }

        okHttpDataSourceFactory = OkHttpDataSource.Factory(client)
            .setUserAgent("VideoPlayer/1.0")

        Log.d(TAG, "buildOkHttpDataSourceFactory:  OkHttpDataSource.Factory 已创建")
        Log.d(TAG, "buildOkHttpDataSourceFactory: User-Agent = VideoPlayer/1.0")
    }

    /**
     * 获取 OkHttpDataSource.Factory
     * 用于 ExoPlayer 和缓存系统
     */
    fun getOkHttpDataSourceFactory(): OkHttpDataSource.Factory {
        val factory = okHttpDataSourceFactory
        if (factory == null) {
            Log.e(TAG, "getOkHttpDataSourceFactory:  Factory 未初始化，请先调用 initialize()")
            throw IllegalStateException("OkHttpManager 未初始化，请在 Application.onCreate() 中调用 initialize()")
        }
        return factory
    }

    /**
     * 获取 OkHttpClient
     * 供外部使用（如自定义网络请求）
     */
    fun getOkHttpClient(): OkHttpClient {
        val client = okHttpClient
        if (client == null) {
            Log.e(TAG, "getOkHttpClient:  Client 未初始化，请先调用 initialize()")
            throw IllegalStateException("OkHttpManager 未初始化")
        }
        return client
    }

    /**
     * 获取网络统计信息
     */
    fun getNetworkStats(): NetworkStats {
        val client = okHttpClient ?: return NetworkStats(0, 0, 0)

        val pool = client.connectionPool
        val idleConnectionCount = pool.connectionCount()

        Log.d(TAG, "getNetworkStats: 空闲连接数 = $idleConnectionCount")

        return NetworkStats(
            idleConnections = idleConnectionCount,
            maxIdleConnections = MAX_IDLE_CONNECTIONS,
            keepAliveMinutes = KEEP_ALIVE_DURATION.toInt()
        )
    }

    /**
     * 网络统计数据类
     */
    data class NetworkStats(
        val idleConnections: Int,      // 当前空闲连接数
        val maxIdleConnections: Int,   // 最大空闲连接数
        val keepAliveMinutes: Int      // 保活时长（分钟）
    )
}
