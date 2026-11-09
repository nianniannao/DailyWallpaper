package com.dailywallpaper.util

import android.app.WallpaperManager
import android.content.Context
import java.io.File
import java.io.FileInputStream

/**
 * 壁纸设置封装：
 * 同时设置主屏幕 + 锁屏（API 24+ 支持），使用流式写入避免大图内存占用。
 */
object WallpaperSetter {

    /**
     * 将本地图片设置为壁纸（主屏+锁屏）。
     * @throws SecurityException 缺少 SET_WALLPAPER 权限时抛出
     */
    fun setWallpaper(context: Context, imageFile: File) {
        val wm = WallpaperManager.getInstance(context)
        FileInputStream(imageFile).use { input ->
            val which = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            // setStream(InputStream, Rect visibleCropHint, boolean allowBackup, int which)
            wm.setStream(input, null, true, which)
        }
    }
}
