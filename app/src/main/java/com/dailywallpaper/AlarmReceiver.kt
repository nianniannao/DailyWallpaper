package com.dailywallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailywallpaper.data.Prefs

/**
 * 接收两类广播：
 * 1) 凌晨 3:00 闹钟触发 → 重排下一天并立即执行换壁纸
 * 2) 开机完成 → 若开启自动更换则恢复调度（闹钟不会跨重启保留）
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            WallpaperScheduler.ALARM_ACTION -> {
                // 触发：先排好明天 3 点，再执行本次更换
                if (Prefs(context).autoEnable) {
                    WallpaperScheduler.schedule(context)
                    WallpaperWorker.enqueueNow(context)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (Prefs(context).autoEnable) {
                    WallpaperScheduler.schedule(context)
                }
            }
        }
    }
}
