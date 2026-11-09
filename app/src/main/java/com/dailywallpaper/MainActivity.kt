package com.dailywallpaper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dailywallpaper.data.Prefs
import com.dailywallpaper.data.WallpaperHistoryItem
import com.dailywallpaper.util.WallpaperSetter
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var ivPreview: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvCopyright: TextView
    private lateinit var tvUpdated: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnSet: MaterialButton
    private lateinit var btnUpdate: MaterialButton
    private lateinit var swAuto: MaterialSwitch
    private lateinit var llExactAlarm: View
    private lateinit var btnExactAlarm: MaterialButton

    private val history = mutableListOf<WallpaperHistoryItem>()
    private var currentIndex = 0

    // 在线补载浏览：已加载到必应第几天（0=今天），最多往前 5 天
    private var onlineDepth = 0
    private val maxOnlineDepth = 5

    private val api = com.dailywallpaper.network.WallpaperApi()

    private var workObserver: androidx.lifecycle.Observer<List<WorkInfo>>? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets()

        prefs = Prefs(this)
        bindViews()
        requestNotificationPermissionIfNeeded()
        setupListeners()
        updateExactAlarmUi()
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后刷新精确闹钟权限状态
        updateExactAlarmUi()
        loadHistory()
    }

    private fun bindViews() {
        ivPreview = findViewById(R.id.ivPreview)
        tvTitle = findViewById(R.id.tvTitle)
        tvCopyright = findViewById(R.id.tvCopyright)
        tvUpdated = findViewById(R.id.tvUpdated)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSet = findViewById(R.id.btnSet)
        btnUpdate = findViewById(R.id.btnUpdate)
        swAuto = findViewById(R.id.swAuto)
        llExactAlarm = findViewById(R.id.llExactAlarm)
        btnExactAlarm = findViewById(R.id.btnExactAlarm)
    }

    private fun setupListeners() {
        swAuto.isChecked = prefs.autoEnable
        swAuto.setOnCheckedChangeListener { _, checked ->
            prefs.autoEnable = checked
            if (checked) {
                WallpaperWorker.scheduleDaily(this)
                Toast.makeText(this, "已开启：每天凌晨 3:00 自动更换", Toast.LENGTH_SHORT).show()
            } else {
                WallpaperWorker.cancelDaily(this)
                Toast.makeText(this, "已关闭自动更换", Toast.LENGTH_SHORT).show()
            }
        }

        // 前后选择：‹ 看更早的，› 看更新的
        // 本地历史翻到最早已下载的壁纸后，自动联网补载更早一天的必应壁纸（最多 5 天前）
        btnPrev.setOnClickListener {
            if (currentIndex < history.size - 1) {
                currentIndex++
                showItem(currentIndex)
            } else if (onlineDepth < maxOnlineDepth) {
                loadEarlierWallpaper()
            } else {
                Toast.makeText(this, "已经是最早的壁纸了（今天往前 $maxOnlineDepth 天）", Toast.LENGTH_SHORT).show()
            }
        }
        btnNext.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                showItem(currentIndex)
            } else {
                Toast.makeText(this, "已经是最新的一张了", Toast.LENGTH_SHORT).show()
            }
        }

        btnSet.setOnClickListener { setCurrentWallpaper() }

        btnUpdate.setOnClickListener { updateNow() }

        btnExactAlarm.setOnClickListener {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "请在系统设置中允许\u201C闹钟与提醒\u201D权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- 壁纸历史与前后选择 ----------

    private fun loadHistory() {
        history.clear()
        history.addAll(prefs.history())
        // 恢复已浏览深度：历史中最大的天数偏移（0=今天）
        onlineDepth = history.maxOfOrNull { it.offset } ?: 0
        if (history.isEmpty()) {
            currentIndex = 0
            ivPreview.setImageResource(R.drawable.bg_placeholder)
            tvTitle.text = getString(R.string.no_wallpaper_yet)
            tvCopyright.visibility = View.GONE
            val last = prefs.lastUpdateDate
            tvUpdated.text = if (last.isEmpty()) {
                getString(R.string.never_updated)
            } else {
                getString(R.string.updated_at, last)
            }
        } else {
            currentIndex = 0
            showItem(0)
        }
    }

    private fun showItem(index: Int) {
        val item = history.getOrNull(index) ?: return
        val file = File(item.path)
        if (!file.exists()) return
        tvTitle.text = item.title.ifEmpty { "必应每日壁纸" }
        tvCopyright.text = item.copyright
        tvCopyright.visibility = if (item.copyright.isEmpty()) View.GONE else View.VISIBLE
        tvUpdated.text = buildString {
            append(getString(R.string.history_pos, index + 1, history.size))
            if (item.date.isNotEmpty()) {
                append(" · ")
                append(getString(R.string.updated_at, item.date))
            }
        }
        loadPreview(file)
    }

    /** 在线补载更早一天的必应壁纸（保持"今天→昨天→前天…"顺序） */
    private fun loadEarlierWallpaper() {
        if (!btnPrev.isEnabled) return
        val targetDepth = onlineDepth + 1
        btnPrev.isEnabled = false
        btnPrev.alpha = 0.5f
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val info = api.fetchBing(targetDepth)
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(
                        java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, -targetDepth)
                        }.time
                    )
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                        .format(java.util.Date())
                    val dir = File(filesDir, "wallpapers")
                    val target = File(dir, "wallpaper_${stamp}.jpg")
                    api.downloadToCache(info.url, target)
                    val item = WallpaperHistoryItem(
                        path = target.absolutePath,
                        title = info.title,
                        copyright = info.copyright,
                        date = date,
                        offset = targetDepth
                    )
                    prefs.appendHistory(item)
                    item
                }
            }
            btnPrev.isEnabled = true
            btnPrev.alpha = 1f
            result.onSuccess { item ->
                onlineDepth = targetDepth
                history.clear()
                history.addAll(prefs.history())
                val idx = history.indexOfFirst { it.path == item.path }
                if (idx >= 0) {
                    currentIndex = idx
                    showItem(idx)
                }
                val days = if (targetDepth == 1) "昨天" else "$targetDepth 天前"
                Toast.makeText(this@MainActivity, "已加载 $days 的必应壁纸", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "加载失败，请检查网络后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadPreview(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = decodeSampledBitmap(file.absolutePath, 1080, 1400)
            withContext(Dispatchers.Main) {
                ivPreview.setImageBitmap(bmp ?: BitmapFactory.decodeResource(resources, R.drawable.bg_placeholder))
            }
        }
    }

    /** 采样解码，避免 4K 大图直接加载导致内存溢出 */
    private fun decodeSampledBitmap(path: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > reqW * 2 || opts.outHeight / sample > reqH * 2) {
                sample *= 2
            }
            val decode = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, decode)
        } catch (_: Exception) {
            null
        }
    }

    private fun setCurrentWallpaper() {
        val item = history.getOrNull(currentIndex) ?: return
        val file = File(item.path)
        if (!file.exists()) {
            Toast.makeText(this, "壁纸文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        btnSet.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { WallpaperSetter.setWallpaper(this@MainActivity, file) }
            }
            btnSet.isEnabled = true
            result.onSuccess {
                Toast.makeText(this@MainActivity, "已设置为主屏与锁屏壁纸", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@MainActivity, "设置失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---------- 立即更新（必应今日壁纸） ----------

    private fun updateNow() {
        btnUpdate.isEnabled = false
        btnUpdate.text = getString(R.string.updating)
        observeWork()
        WallpaperWorker.enqueueNow(this)
    }

    private fun observeWork() {
        val liveData = WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(WallpaperWorker.NOW_NAME)
        workObserver?.let { liveData.removeObserver(it) }
        workObserver = androidx.lifecycle.Observer { infos ->
            val last = infos.lastOrNull() ?: return@Observer
            when (last.state) {
                WorkInfo.State.SUCCEEDED -> {
                    resetUpdateButton()
                    loadHistory()
                    Toast.makeText(this@MainActivity, "今日壁纸已更新", Toast.LENGTH_SHORT).show()
                    removeWorkObserver()
                }
                WorkInfo.State.FAILED -> {
                    resetUpdateButton()
                    Toast.makeText(this@MainActivity, "获取失败：请检查网络后重试", Toast.LENGTH_LONG).show()
                    removeWorkObserver()
                }
                else -> { /* 运行中 */ }
            }
        }
        workObserver?.let { liveData.observe(this, it) }
    }

    private fun removeWorkObserver() {
        workObserver?.let {
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(WallpaperWorker.NOW_NAME).removeObserver(it)
        }
        workObserver = null
    }

    private fun resetUpdateButton() {
        btnUpdate.isEnabled = true
        btnUpdate.text = getString(R.string.update_now)
    }

    // ---------- 权限 ----------

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateExactAlarmUi() {
        val needExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !WallpaperScheduler.canScheduleExact(this)
        llExactAlarm.visibility = if (needExact) View.VISIBLE else View.GONE
        // 自动更换开启时保证已注册调度
        if (prefs.autoEnable) {
            WallpaperWorker.scheduleDaily(this)
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }
}
