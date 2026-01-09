package com.example.videoplayer

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.videoplayer.adapter.ViewPagerAdapter
import com.example.videoplayer.data.AppDatabase
import com.example.videoplayer.data.VideoEntity
import com.example.videoplayer.databinding.ActivityMainBinding
import com.example.videoplayer.network.NetworkConfig
import com.example.videoplayer.network.RetrofitClient
import com.example.videoplayer.utils.setupImmersiveUI
import com.example.videoplayer.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 主Activity
 *
 * 单 Activity 架构，使用 ViewPager2 + BottomNavigationView 管理三个主页面：
 * - 首页（VideoFragment）
 * - 好友（ContactsFragment）
 * - 我的（ProfileFragment）
 *
 * 迭代12新增：视频发布功能
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: VideoViewModel by viewModels()
    private lateinit var viewPagerAdapter: ViewPagerAdapter

    //  迭代15：视频同步标志，防止重复同步
    private var hasSyncedVideos = false

    // 迭代12：媒体选择器
    // 相册视频选择器
    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            Log.d(TAG, "pickVideo: 选中视频 URI=$it")
            showPublishVideoDialog(it)
        }
    }

    // 录制视频
    private val captureVideo = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && videoUri != null) {
            Log.d(TAG, "captureVideo: 录制成功 URI=$videoUri")
            showPublishVideoDialog(videoUri!!)
        } else {
            Log.e(TAG, "captureVideo: 录制失败或取消")
            Toast.makeText(this, "录制失败", Toast.LENGTH_SHORT).show()
        }
    }

    private var videoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity 开始创建")

        // 设置沉浸式 UI
        setupImmersiveUI()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        setupPublishButton()  // 迭代12：配置发布按钮

        Log.d(TAG, "onCreate: Activity 创建完成")
    }

    /**
     * 配置 ViewPager2
     */
    private fun setupViewPager() {
        Log.d(TAG, "setupViewPager: 配置 ViewPager2")

        viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        // 禁止用户手指滑动切换页面（只能通过底部导航切换）
        binding.viewPager.isUserInputEnabled = false
        Log.d(TAG, "setupViewPager: 已禁用 ViewPager2 的滑动切换")

        // 监听页面切换事件
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d(TAG, "onPageSelected: 页面切换到 position=$position")

                // 同步更新底部导航栏的选中状态
                when (position) {
                    0 -> binding.bottomNav.selectedItemId = R.id.nav_video
                    1 -> binding.bottomNav.selectedItemId = R.id.nav_contacts
                    2 -> binding.bottomNav.selectedItemId = R.id.nav_profile
                }
            }
        })

        Log.d(TAG, "setupViewPager: ViewPager2 配置完成")
    }

    /**
     * 配置底部导航栏
     */
    private fun setupBottomNavigation() {
        Log.d(TAG, "setupBottomNavigation: 配置底部导航栏")

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_video -> {
                    Log.d(TAG, "bottomNav: 切换到\"首页\"")
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_contacts -> {
                    Log.d(TAG, "bottomNav: 切换到\"好友\"")
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_profile -> {
                    Log.d(TAG, "bottomNav: 切换到\"我的\"")
                    binding.viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }

        // 默认选中"首页"
        binding.bottomNav.selectedItemId = R.id.nav_video
        Log.d(TAG, "setupBottomNavigation: 底部导航栏配置完成，默认选中\"首页\"")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity 可见")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity 获得焦点")

        //  迭代15：首次恢复时同步服务器视频
        if (!hasSyncedVideos) {
            lifecycleScope.launch {
                try {
                    // 延迟1秒，确保Socket连接稳定
                    kotlinx.coroutines.delay(1000)

                    Log.d(TAG, "onResume: 开始同步服务器视频列表...")
                    val repository = com.example.videoplayer.repository.VideoRepository(this@MainActivity)
                    repository.syncVideosFromServer()
                    hasSyncedVideos = true
                    Log.d(TAG, "onResume:  视频同步完成")
                } catch (e: Exception) {
                    Log.e(TAG, "onResume:  视频同步失败", e)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Activity 失去焦点")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Activity 不可见")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity 销毁")
    }

    // ========== 迭代12：视频发布功能 ==========

    /**
     * 配置发布按钮
     */
    private fun setupPublishButton() {
        binding.fabPublish.setOnClickListener {
            Log.d(TAG, "setupPublishButton: 点击发布按钮")
            showPublishDialog()
        }
    }

    /**
     * 显示视频发布对话框
     */
    private fun showPublishVideoDialog(uri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_publish_video, null)
        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etTitle)
        val etDescription = dialogView.findViewById<android.widget.EditText>(R.id.etDescription)

        // 设置默认值
        etTitle.setText("我的作品_${System.currentTimeMillis()}")
        etDescription.setText("我刚刚发布的视频 #原创 #我的作品")

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.Button>(R.id.btnPublish).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()

            if (title.isEmpty()) {
                Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            uploadAndPublishVideo(uri, title, description)
        }

        dialog.show()
    }

    /**
     * 显示发布选项对话框
     */
    private fun showPublishDialog() {
        val options = arrayOf("从相册选择", "录制视频")
        AlertDialog.Builder(this)
            .setTitle("发布视频")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // 从相册选择
                        Log.d(TAG, "showPublishDialog: 选择\"从相册选择\"")
                        pickVideo.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                    1 -> {
                        // 录制视频
                        Log.d(TAG, "showPublishDialog: 选择\"录制视频\"")
                        val tempFile = File(cacheDir, "video_${System.currentTimeMillis()}.mp4")
                        videoUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            tempFile
                        )
                        videoUri?.let { captureVideo.launch(it) }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }


    /**
     * 提取视频封面（第一帧）
     *
     * @param videoUri 视频URI
     * @return File? 封面图片文件，提取失败返回null
     */
    private suspend fun extractVideoFrame(videoUri: Uri): File? {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                // 设置数据源
                retriever.setDataSource(this@MainActivity, videoUri)

                // 获取视频第一帧（时间为0微秒）
                val bitmap = retriever.getFrameAtTime(
                    0,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                if (bitmap != null) {
                    // 保存为临时文件
                    val coverFile = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(coverFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    bitmap.recycle()
                    Log.d(TAG, "extractVideoFrame: 封面提取成功 path=${coverFile.absolutePath}")
                    coverFile
                } else {
                    Log.e(TAG, "extractVideoFrame: 无法提取视频帧")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractVideoFrame: 提取视频帧失败", e)
                null
            } finally {
                retriever.release()
            }
        }
    }


    /**
     * 上传视频并发布到数据库
     */
    private fun uploadAndPublishVideo(uri: Uri, title: String, description: String) {
        Log.d(TAG, "uploadAndPublishVideo: 开始上传视频 URI=$uri, title=$title")
        Toast.makeText(this, "正在上传视频...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            var tempFile: File? = null
            var coverFile: File? = null
            try {
                // Step 1: 将URI内容复制到临时文件
                tempFile = withContext(Dispatchers.IO) {
                    val file = File(cacheDir, "upload_${System.currentTimeMillis()}.mp4")
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    file
                }
                Log.d(TAG, "uploadAndPublishVideo: 临时文件创建成功 path=${tempFile.absolutePath}")

                // Step 2: 提取视频封面
                Log.d(TAG, "uploadAndPublishVideo: 开始提取视频封面")
                coverFile = extractVideoFrame(uri)
                if (coverFile == null) {
                    Log.w(TAG, "uploadAndPublishVideo: 封面提取失败，将继续上传但不带封面")
                } else {
                    Log.d(TAG, "uploadAndPublishVideo: 封面提取成功 path=${coverFile.absolutePath}")
                }

                // Step 3: 构建Multipart请求
                val videoRequestBody = tempFile.asRequestBody("video/*".toMediaTypeOrNull())
                val videoPart = MultipartBody.Part.createFormData("video", tempFile.name, videoRequestBody)

                // Step 4: 上传到服务器（视频+封面）
                Log.d(TAG, "uploadAndPublishVideo: 开始上传到服务器")
                val response = withContext(Dispatchers.IO) {
                    if (coverFile != null) {
                        // 同时上传视频和封面
                        val coverRequestBody = coverFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val coverPart = MultipartBody.Part.createFormData("cover", coverFile.name, coverRequestBody)
                        RetrofitClient.apiService.uploadVideoWithCover(videoPart, coverPart).execute()
                    } else {
                        // 只上传视频
                        RetrofitClient.apiService.uploadVideo(videoPart).execute()
                    }
                }

                if (response.isSuccessful && response.body()?.status == "success") {
                    val videoUrl = response.body()?.url
                    val coverUrl = response.body()?.coverUrl
                    Log.d(TAG, "uploadAndPublishVideo: 上传成功 videoUrl=$videoUrl, coverUrl=$coverUrl")

                    // Step 5: 插入数据库
                    if (videoUrl != null) {
                        val sessionManager = com.example.videoplayer.utils.SessionManager.getInstance(this@MainActivity)
                        val currentUserId = sessionManager.currentUserId

                        val videoEntity = VideoEntity(
                            id = "video_${UUID.randomUUID()}",
                            title = title,
                            videoUrl = videoUrl,
                            coverUrl = coverUrl ?: NetworkConfig.getDefaultCoverUrl("upload"),  // 使用服务器返回的封面URL
                            authorId = currentUserId,
                            authorName = currentUserId,
                            authorAvatarUrl = NetworkConfig.getDefaultAvatarUrl(currentUserId),
                            description = description,
                            likeCount = 0,
                            commentCount = 0
                        )

                        Log.d(TAG, "uploadAndPublishVideo: 准备插入数据库:")
                        Log.d(TAG, "  - videoId: ${videoEntity.id}")
                        Log.d(TAG, "  - coverUrl: ${videoEntity.coverUrl}")
                        Log.d(TAG, "  - authorId: ${videoEntity.authorId}")
                        Log.d(TAG, "  - authorName: ${videoEntity.authorName}")

                        withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(this@MainActivity).videoDao().insertVideo(videoEntity)
                        }

                        // Step 6: 发布视频到服务器
                        try {
                            val videoJson = org.json.JSONObject().apply {
                                put("id", videoEntity.id)
                                put("title", videoEntity.title)
                                put("videoUrl", videoEntity.videoUrl)
                                put("coverUrl", videoEntity.coverUrl)
                                put("authorId", videoEntity.authorId)
                                put("authorName", videoEntity.authorName)
                                put("authorAvatarUrl", videoEntity.authorAvatarUrl)
                                put("description", videoEntity.description)
                                put("likeCount", videoEntity.likeCount)
                                put("commentCount", videoEntity.commentCount)
                            }
                            com.example.videoplayer.network.SocketManager.publishVideo(videoJson)
                            Log.d(TAG, "uploadAndPublishVideo: 视频已发布到服务器")
                        } catch (e: Exception) {
                            Log.e(TAG, "uploadAndPublishVideo: 发布到服务器失败（本地已保存）", e)
                        }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "发布成功！", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "uploadAndPublishVideo: 视频已插入数据库")
                            binding.viewPager.setCurrentItem(0, true)
                        }
                    }
                } else {
                    val errorMsg = response.body()?.message ?: "上传失败"
                    Log.e(TAG, "uploadAndPublishVideo: 上传失败: $errorMsg")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "上传失败: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "uploadAndPublishVideo: 上传异常", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                // 清理临时文件
                tempFile?.delete()
                coverFile?.delete()
            }
        }
    }
}
