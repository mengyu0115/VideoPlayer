package com.example.videoplayer.network

/**
 * 网络配置类
 *
 * 定义服务器连接信息和网络相关常量
 */
object NetworkConfig {
    /**
     * 服务器基础 URL
     *
     * 注意：使用真实的局域网 IP，不是 localhost 或 10.0.2.2
     * 真机调试时，手机必须连接到同一个 Wi-Fi
     */
    const val BASE_URL = "http://10.29.209.85:8081"

    /**
     * 视频文件夹路径
     */
    const val VIDEO_PATH = "/videos/"

    /**
     * 封面图片文件夹路径
     */
    const val COVER_PATH = "/covers/"

    /**
     * 获取视频完整 URL
     *
     * @param videoFileName 视频文件名（如 "v1.mp4"）
     * @return 完整的视频 URL
     */
    fun getVideoUrl(videoFileName: String): String {
        return "$BASE_URL$VIDEO_PATH$videoFileName"
    }

    /**
     * 获取视频封面 URL
     *
     * @param videoId 视频ID（如 "v1"）
     * @return 封面图片 URL
     */
    fun getCoverUrl(videoId: String): String {
        // 优先使用服务器提供的封面图片
        return "$BASE_URL$COVER_PATH${videoId}.jpg"
    }

    /**
     * 获取默认头像 URL（使用占位图服务）
     *
     * @param seed 种子值，用于生成不同的头像
     * @return 头像 URL
     */
    fun getDefaultAvatarUrl(seed: String): String {
        return "https://picsum.photos/seed/$seed/200/200"
    }

    /**
     * 获取默认封面 URL
     *
     * @param seed 种子值
     * @return 封面 URL
     */
    fun getDefaultCoverUrl(seed: String): String {
        return "https://picsum.photos/seed/$seed/1080/1920"
    }
}

