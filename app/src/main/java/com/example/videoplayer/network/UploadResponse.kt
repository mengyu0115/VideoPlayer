package com.example.videoplayer.network

/**
 * 视频上传响应
 *
 * 服务器返回的JSON格式：
 * {
 *   "status": "success",
 *   "url": "http://10.29.209.85:8081/videos/xxx.mp4",
 *   "coverUrl": "http://10.29.209.85:8081/covers/xxx.jpg",
 *   "message": "上传成功"
 * }
 */
data class UploadResponse(
    val status: String,         // 状态：success 或 error
    val url: String?,           // 视频URL（上传成功时返回）
    val coverUrl: String?,      // 封面URL（上传成功时返回）
    val message: String?        // 提示信息
)
