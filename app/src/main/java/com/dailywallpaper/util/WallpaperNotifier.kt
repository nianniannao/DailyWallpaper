package com.dailywallpaper.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dailywallpaper.MainActivity
import com.dailywallpaper.R
import java.io.File

/** 每日壁纸更新通知 */
object WallpaperNotifier {

    const val CHANNEL_ID = "wallpaper_updates"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "壁纸更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "每日壁纸自动更换完成后的通知"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun notifyWallpaperUpdated(context: Context, title: String, copyright: String, imageFile: File?) {
        // 用户未授予通知权限（Android 13+）时静默跳过，不影响换壁纸功能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("每日壁纸已更新")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (copyright.isBlank()) title else "$title\n$copyright"
            ))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // 附上壁纸缩略图（存在本地文件时）
        if (imageFile != null && imageFile.exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeFile(imageFile.absolutePath, opts)
                if (bmp != null) builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
            } catch (_: Exception) {
                // 缩略图失败不影响通知
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 忽略：无通知权限
        }
    }
}
