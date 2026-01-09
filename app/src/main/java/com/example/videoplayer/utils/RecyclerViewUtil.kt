package com.example.videoplayer.utils

import android.graphics.Rect
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val TAG = "RecyclerViewUtil"

/**
 * 计算屏幕中心占比最大的 Item 位置
 * @return 占比最大的 Item Position，如果没有找到返回 -1
 */
fun RecyclerView.findCenterVisibleItemPosition(): Int {
    Log.d(TAG, "findCenterVisibleItemPosition: 开始查找中心可见 Item")
    // 获取 LinearLayoutManager
    val layoutManager = this.layoutManager as? LinearLayoutManager
    if (layoutManager == null) {
        Log.e(TAG, "findCenterVisibleItemPosition: LayoutManager 不是 LinearLayoutManager")
        return -1
    }
    //firstVisiblePosition为0，表示第一个可见的 Item 的 position
    val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
    //lastVisiblePosition为-1，表示最后一个可见的 Item 的 position
    val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

    Log.d(TAG, "findCenterVisibleItemPosition: 可见范围 $firstVisiblePosition ~ $lastVisiblePosition")

    if (firstVisiblePosition == RecyclerView.NO_POSITION) {
        Log.w(TAG, "findCenterVisibleItemPosition: 没有可见的 Item")
        return -1
    }

    // 获取 RecyclerView 的中心点
    val recyclerViewRect = Rect()
    this.getGlobalVisibleRect(recyclerViewRect)
    val recyclerViewCenterY = recyclerViewRect.centerY()
    Log.d(TAG, "findCenterVisibleItemPosition: RecyclerView 中心 Y 坐标 = $recyclerViewCenterY")

    var maxVisibleArea = 0f
    var centerPosition = firstVisiblePosition

    // 遍历所有可见的 Item
    for (position in firstVisiblePosition..lastVisiblePosition) {
        // 获取 Item 的可见区域
        val view = layoutManager.findViewByPosition(position) ?: continue
        //Rect对象用于表示一个矩形区域
        val viewRect = Rect()
        view.getGlobalVisibleRect(viewRect)

        // 计算 Item 在 RecyclerView 中的可见高度
        val visibleHeight = viewRect.height().toFloat()
        val totalHeight = view.height.toFloat()

        if (totalHeight > 0) {
            val visibleRatio = visibleHeight / totalHeight

            Log.v(TAG, "findCenterVisibleItemPosition: position=$position, " +
                    "visibleHeight=$visibleHeight, totalHeight=$totalHeight, " +
                    "visibleRatio=${String.format("%.2f", visibleRatio)}")

            // 找到可见比例最大的 Item
            if (visibleHeight > maxVisibleArea) {
                maxVisibleArea = visibleHeight
                centerPosition = position
            }
        }
    }

    Log.d(TAG, "findCenterVisibleItemPosition: 中心位置 = $centerPosition, 最大可见高度 = $maxVisibleArea")
    return centerPosition
}

/**
 * 获取指定位置的 ViewHolder
 */
fun RecyclerView.findViewHolderForPosition(position: Int): RecyclerView.ViewHolder? {
    val viewHolder = findViewHolderForAdapterPosition(position)
    if (viewHolder != null) {
        Log.d(TAG, "findViewHolderForPosition: 找到 position=$position 的 ViewHolder, hashCode=${viewHolder.hashCode()}")
    } else {
        Log.w(TAG, "findViewHolderForPosition: 未找到 position=$position 的 ViewHolder")
    }
    return viewHolder
}
