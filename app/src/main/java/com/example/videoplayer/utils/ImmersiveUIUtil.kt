package com.example.videoplayer.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.core.view.WindowCompat

private const val TAG = "ImmersiveUIUtil"

/**
 * 设置沉浸式状态栏（适配 Android 15+ Edge-to-Edge）
 *
 * Android 15 (API 35+) 架构变更：
 * - 系统强制推行 Edge-to-Edge（边到边）体验
 * - statusBarColor 和 navigationBarColor 已被废弃
 * - 系统自动管理状态栏和导航栏透明度
 *
 * 新的实现方式：
 * - 使用 WindowCompat.setDecorFitsSystemWindows(false) 启用 Edge-to-Edge
 * - 系统自动处理状态栏和导航栏的透明度
 * - 内容自动延伸到系统栏下方
 * - 使用 WindowInsetsController 控制图标颜色
 */
fun Activity.setupImmersiveUI() {
//    Log.d(TAG, "setupImmersiveUI: 开始设置沉浸式 UI")
//    Log.d(TAG, "setupImmersiveUI: Android SDK 版本: ${Build.VERSION.SDK_INT}")

    // 启用 Edge-to-Edge 模式（适配 Android 15+）
    // 允许内容延伸到系统栏（状态栏和导航栏）下方
    // 系统会自动将状态栏和导航栏设为透明
//    Log.d(TAG, "setupImmersiveUI: 启用 Edge-to-Edge 模式")
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // 获取 WindowInsetsController 控制系统栏图标颜色
//    Log.d(TAG, "setupImmersiveUI: 获取 WindowInsetsController")
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)

    // 设置状态栏和导航栏为深色图标模式（浅色文字/按钮）
    // 适合深色背景的应用（如视频播放器）
    insetsController.apply {
//        Log.d(TAG, "setupImmersiveUI: 设置状态栏为深色图标模式（浅色文字）")
        // isAppearanceLightStatusBars = false 表示使用浅色内容（白色文字和图标）
        isAppearanceLightStatusBars = false

//        Log.d(TAG, "setupImmersiveUI: 设置导航栏为深色图标模式（浅色按钮）")
        // isAppearanceLightNavigationBars = false 表示导航栏使用浅色内容
        isAppearanceLightNavigationBars = false
    }

//    Log.d(TAG, "setupImmersiveUI:  Edge-to-Edge 沉浸式 UI 设置完成")
}
