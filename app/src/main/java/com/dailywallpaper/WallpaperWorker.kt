package com.dailywallpaper

import android.content.Context
import android.util.Log
import androidx.work.*
import androidx.work.ExistingWorkPolicy
import com.dailywallpaper.data.Prefs
import com.dailywallpaper.data.WallpaperHistoryItem
import com.dailywallpaper.network.WallpaperApi
import com.dailywallpaper.util.WallpaperNotifier
import com.dailywallpaper.util.WallpaperSetter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 壁纸任务：必应每日图片（4K UHD）→ 下载 → 保存历史 → 设置主屏+锁屏 → 通知。
 * 由 AlarmManager（凌晨 3 点）或手动按钮触发。
 */
class WallpaperWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val api = WallpaperApi()
        val historyDir = File(applicationContext.filesDir, "wallpapers")

        try {
            val info = api.fetchBing()
            // 下载到历史目录（文件名带时间戳，天然去重）
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val target = File(historyDir, "wallpaper_$stamp.jpg")
            api.downloadToCache(info.url, target)

            // 设置壁纸（主屏 + 锁屏）
            WallpaperSetter.setWallpaper(applicationContext, target)

            // 记录状态与历史（最多保留 30 张，旧文件一并清理）
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
            prefs.lastImagePath = target.absolutePath
            prefs.lastTitle = info.title
            prefs.lastCopyright = info.copyright
            prefs.lastUpdateDate = now
            prefs.addHistory(
                WallpaperHistoryItem(target.absolutePath, info.title, info.copyright, now)
            )
            cleanupHistory(historyDir, prefs)

            WallpaperNotifier.notifyWallpaperUpdated(applicationContext, info.title, info.copyright, target)
            Log.i(TAG, "壁纸更新成功：${info.title}")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "壁纸更新失败", e)
            // 网络/接口瞬时问题：指数退避重试
            return Result.retry()
        }
    }

    /** 清理超出上限的旧历史文件 */
    private fun cleanupHistory(dir: File, prefs: Prefs) {
        try {
            val keep = prefs.history().map { it.path }.toSet()
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("wallpaper_") && f.absolutePath !in keep) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
            // 清理失败不影响主流程
        }
    }

    companion object {
        private const val TAG = "WallpaperWorker"

        const val NOW_NAME = "daily_wallpaper_now"

        /** 立即更新一次（手动按钮 / 凌晨 3 点触发） */
        fun enqueueNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        /** 注册每日凌晨 3:00 自动更换（AlarmManager 精确调度） */
        fun scheduleDaily(context: Context) {
            WallpaperScheduler.schedule(context)
        }

        /** 取消自动更换 */
        fun cancelDaily(context: Context) {
            WallpaperScheduler.cancel(context)
        }
    }
}
