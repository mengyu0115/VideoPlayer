package com.example.videoplayer.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * API Service Interface
 * Defines all APIs for server interaction
 */
interface ApiService {

    /**
     * Upload video to server
     * @param video MultipartBody.Part for video file
     * @return UploadResponse containing uploaded video URL
     */
    @Multipart
    @POST("/upload")
    fun uploadVideo(
        @Part video: MultipartBody.Part
    ): Call<UploadResponse>
}
