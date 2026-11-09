package com.dailywallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 每日凌晨 3:00 精确自动更换壁纸的调度器。
 *
 * 为什么不用 WorkManager 周期任务：WorkManager 的 24h 周期任务由系统统一调度，
 * 受省电策略影响，很多机型不会在预定时间执行甚至长期不触发（即用户遇到的"无法自动更换"）。
 * 改用 AlarmManager 的 setExactAndAllowWhileIdle：熄屏/Doze 下仍能准时触发；
 * 无精确闹钟权限时降级为非精确（每天 3 点附近）并提示授权。
 */
object WallpaperScheduler {

    const val ALARM_ACTION = "com.dailywallpaper.action.WALLPAPER_ALARM"
    private const val REQUEST_CODE = 2026

    /** 是否具备精确闹钟能力（Android 12+） */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** 注册每日凌晨 3:00 自动更换；已有任务则更新 */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        val next = nextTriggerAt()
        try {
            if (canScheduleExact(context)) {
                // 精确：Doze/熄屏下仍准时触发
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                // 降级：非精确 24h 周期，每天 3 点附近执行
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, pi)
            }
        } catch (e: Exception) {
            // 极端情况（如部分厂商限制）：再降级为非精确周期
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, pi)
        }
    }

    /** 取消自动更换 */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pendingIntent(context))
    }

    /** 下一次凌晨 3:00 的时间戳（已过则明天） */
    private fun nextTriggerAt(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 3)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ALARM_ACTION
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
