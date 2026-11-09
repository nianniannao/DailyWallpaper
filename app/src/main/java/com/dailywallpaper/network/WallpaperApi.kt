package com.dailywallpaper.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 一张壁纸的元信息 */
data class WallpaperInfo(
    val url: String,        // 高清图直链（4K UHD）
    val title: String,      // 标题/说明
    val copyright: String   // 版权/拍摄信息（可为空）
)

/**
 * 壁纸源网络层：必应每日图片（Bing HPImageArchive，UHD 3840×2160 4K）
 */
class WallpaperApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val userAgent = "DailyWallpaper/2.0 (Android; compatible)"

    /** 获取必应每日图片元信息；idx=0 今天，1 昨天，2 前天……最多 7（8 天前） */
    suspend fun fetchBing(idx: Int = 0): WallpaperInfo = withContext(Dispatchers.IO) {
        val safeIdx = idx.coerceIn(0, 7)
        val url = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=$safeIdx&n=1&mkt=zh-CN"
        val body = getString(url)
        val json = JSONObject(body)
        val images = json.optJSONArray("images")
        if (images == null || images.length() == 0) {
            throw IOException("必应接口返回为空")
        }
        val first = images.getJSONObject(0)
        val rel = first.optString("url")
        if (rel.isEmpty()) throw IOException("必应接口缺少图片地址")
        // 补全绝对地址并升级为 UHD 4K（3840×2160）
        val base = if (rel.startsWith("http")) rel else "https://www.bing.com$rel"
        val uhd = base
            .replace("_1920x1080.jpg", "_UHD.jpg")
            .replace("_1920x1200.jpg", "_UHD.jpg")
            .replace("_1366x768.jpg", "_UHD.jpg")
            .let { if (it.contains("w=")) it else "$it&w=3840&h=2160" }
        WallpaperInfo(
            url = uhd,
            title = first.optString("title").ifEmpty { "必应每日壁纸" },
            copyright = first.optString("copyright")
        )
    }

    /** 下载图片字节，返回目标文件 */
    suspend fun downloadToCache(url: String, target: File): File = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下载图片失败 HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("下载图片失败：响应体为空")
            val bytes = body.bytes()
            if (bytes.size < 1024) throw IOException("下载图片失败：文件过小")
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            target
        }
    }

    private fun getString(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("请求失败 HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }
}
