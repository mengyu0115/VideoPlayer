package com.example.videoplayer.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * ApiService 接口
 * 负责上传视频
 */
interface ApiService {

    /**
     * 上传视频到服务器
     * @param video 待上传的视频文件
     * @return 响应结果
     */
    @Multipart
    @POST("/upload")
    fun uploadVideo(
        @Part video: MultipartBody.Part
    ): Call<UploadResponse>

    /**
     * 上传视频和封面到服务器
     * @param video 视频文件
     * @param cover 封面文件
     * @return 返回上传结果
     */
    //MultipartBody是一个用于处理文件上传的类，它可以将多个文件或参数封装到一个对象中，然后进行上传。
    @Multipart
    @POST("/upload")
    fun uploadVideoWithCover(
        @Part video: MultipartBody.Part,
        @Part cover: MultipartBody.Part
    ): Call<UploadResponse>
}
