package com.example.videoplayer.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit客户端单例
 *
 * 提供ApiService实例，用于与服务器通信
 */
object RetrofitClient {

    // 服务器基础URL（使用NetworkConfig中的配置）
    private val BASE_URL = NetworkConfig.BASE_URL

    // OkHttp客户端配置（增加超时时间，适合大文件上传）
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)      // 连接超时
        .readTimeout(60, TimeUnit.SECONDS)          // 读取超时
        .writeTimeout(60, TimeUnit.SECONDS)         // 写入超时（上传时间）
        .build()

    // Retrofit实例（懒加载）
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())  // 使用Gson解析JSON
            .build()
    }

    // ApiService实例（懒加载）
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

