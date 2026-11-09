package com.dailywallpaper.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** 单张历史壁纸记录 */
data class WallpaperHistoryItem(
    val path: String,      // 本地文件绝对路径
    val title: String,
    val copyright: String,
    val date: String,      // 获取时间/对应日期
    val offset: Int = 0    // 必应天数偏移：0=今天，1=昨天……
)

/**
 * 轻量设置存储：自动更换开关、壁纸历史（最近 30 张）、最近一次壁纸信息。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("daily_wallpaper", Context.MODE_PRIVATE)

    /** 是否每日凌晨 3 点自动更换 */
    var autoEnable: Boolean
        get() = sp.getBoolean(KEY_AUTO, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO, value).apply()

    /** 最近一次壁纸信息（用于默认展示） */
    var lastImagePath: String
        get() = sp.getString(KEY_LAST_PATH, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_PATH, value).apply()

    var lastTitle: String
        get() = sp.getString(KEY_LAST_TITLE, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_TITLE, value).apply()

    var lastCopyright: String
        get() = sp.getString(KEY_LAST_COPY, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_COPY, value).apply()

    var lastUpdateDate: String
        get() = sp.getString(KEY_LAST_DATE, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_DATE, value).apply()

    // ---------- 壁纸历史 ----------

    /** 新增一条历史（最新的在最前），自动裁剪到上限 */
    fun addHistory(item: WallpaperHistoryItem) {
        val paths = strList(KEY_H_PATHS).toMutableList()
        val titles = strList(KEY_H_TITLES).toMutableList()
        val copyrights = strList(KEY_H_COPY).toMutableList()
        val dates = strList(KEY_H_DATE).toMutableList()
        val offsets = intList(KEY_H_OFFSET).toMutableList()

        // 去重：同一路径不重复添加
        val idx = paths.indexOf(item.path)
        if (idx >= 0) {
            paths.removeAt(idx); titles.removeAt(idx); copyrights.removeAt(idx)
            dates.removeAt(idx); offsets.removeAt(idx)
        }
        paths.add(0, item.path)
        titles.add(0, item.title)
        copyrights.add(0, item.copyright)
        dates.add(0, item.date)
        offsets.add(0, item.offset)

        while (paths.size > MAX_HISTORY) {
            paths.removeAt(paths.size - 1)
            titles.removeAt(titles.size - 1)
            copyrights.removeAt(copyrights.size - 1)
            dates.removeAt(dates.size - 1)
            offsets.removeAt(offsets.size - 1)
        }
        sp.edit()
            .putString(KEY_H_PATHS, JSONArray(paths).toString())
            .putString(KEY_H_TITLES, JSONArray(titles).toString())
            .putString(KEY_H_COPY, JSONArray(copyrights).toString())
            .putString(KEY_H_DATE, JSONArray(dates).toString())
            .putString(KEY_H_OFFSET, JSONArray(offsets).toString())
            .apply()
    }

    /** 历史列表（新在前） */
    fun history(): List<WallpaperHistoryItem> {
        val paths = strList(KEY_H_PATHS)
        if (paths.isEmpty()) return emptyList()
        val titles = strList(KEY_H_TITLES)
        val copyrights = strList(KEY_H_COPY)
        val dates = strList(KEY_H_DATE)
        val offsets = intList(KEY_H_OFFSET)
        return paths.mapIndexed { i, p ->
            WallpaperHistoryItem(
                path = p,
                title = if (i < titles.size) titles[i] else "",
                copyright = if (i < copyrights.size) copyrights[i] else "",
                date = if (i < dates.size) dates[i] else "",
                offset = if (i < offsets.size) offsets[i] else 0
            )
        }
    }

    /**
     * 追加到历史末尾（用于在线补载更早日期的壁纸，保持"今天→昨天→前天…"顺序）。
     * 不改变已有条目顺序，超限时移除最早的（末尾）。
     */
    fun appendHistory(item: WallpaperHistoryItem) {
        val paths = strList(KEY_H_PATHS).toMutableList()
        val titles = strList(KEY_H_TITLES).toMutableList()
        val copyrights = strList(KEY_H_COPY).toMutableList()
        val dates = strList(KEY_H_DATE).toMutableList()
        val offsets = intList(KEY_H_OFFSET).toMutableList()

        val idx = paths.indexOf(item.path)
        if (idx >= 0) {
            // 已存在：移到末尾（视为再次访问）
            paths.removeAt(idx); titles.removeAt(idx); copyrights.removeAt(idx)
            dates.removeAt(idx); offsets.removeAt(idx)
        }
        paths.add(item.path)
        titles.add(item.title)
        copyrights.add(item.copyright)
        dates.add(item.date)
        offsets.add(item.offset)

        while (paths.size > MAX_HISTORY) {
            paths.removeAt(0)
            titles.removeAt(0)
            copyrights.removeAt(0)
            dates.removeAt(0)
            offsets.removeAt(0)
        }
        sp.edit()
            .putString(KEY_H_PATHS, JSONArray(paths).toString())
            .putString(KEY_H_TITLES, JSONArray(titles).toString())
            .putString(KEY_H_COPY, JSONArray(copyrights).toString())
            .putString(KEY_H_DATE, JSONArray(dates).toString())
            .putString(KEY_H_OFFSET, JSONArray(offsets).toString())
            .apply()
    }

    private fun strList(key: String): List<String> {
        val raw = sp.getString(key, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun intList(key: String): List<Int> {
        val raw = sp.getString(key, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optInt(it, 0) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val MAX_HISTORY = 30

        private const val KEY_AUTO = "auto_enable"
        private const val KEY_LAST_PATH = "last_path"
        private const val KEY_LAST_TITLE = "last_title"
        private const val KEY_LAST_COPY = "last_copy"
        private const val KEY_LAST_DATE = "last_date"
        private const val KEY_H_PATHS = "h_paths"
        private const val KEY_H_TITLES = "h_titles"
        private const val KEY_H_COPY = "h_copy"
        private const val KEY_H_DATE = "h_date"
        private const val KEY_H_OFFSET = "h_offset"
    }
}
