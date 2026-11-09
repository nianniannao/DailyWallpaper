# 每日壁纸 DailyWallpaper

一款极简的安卓每日壁纸应用：联网抓取 **必应每日图片（4K UHD）**，每天凌晨 3:00 自动设置为主屏幕与锁屏壁纸，支持壁纸历史前后选择与**当日前 5 天壁纸在线浏览**。

> 当前版本：**v2.0.1**（versionCode 6）

## 下载安装

- **APK 安装包**：[DailyWallpaper-2.0.1.apk](apk/DailyWallpaper-2.0.1.apk)（5.8 MB）
- **Releases 页面**：<https://github.com/nianniannao/DailyWallpaper/releases/latest>

安装步骤：下载 APK → 手机点击安装（允许未知来源）→ 首次打开时允许闹钟与提醒权限，以保证凌晨 3 点准时自动更换壁纸。

## 功能特性

- **必应每日 4K 壁纸（唯一来源）**：每日自动获取必应首页精选图（UHD 3840×2160 真 4K）
- **凌晨 3 点精确自动更换**：使用 **AlarmManager 精确闹钟**（`setExactAndAllowWhileIdle`），熄屏/Doze 下也能在 03:00 准时触发；开机后自动恢复调度；无精确闹钟权限时自动降级并引导授权
- **壁纸历史 + 前后选择**：每次更换自动保存历史（最多 30 张，存于应用目录）；预览图左右 `‹ ›` 切换浏览；本地历史翻到底时自动联网补载更早一天的必应壁纸（今天→昨天→前天…最多 5 天前）
- **主屏 + 锁屏同步设置**：一次性同时更换
- **极简权限**：联网 + 设置壁纸 + 通知（可选）+ 精确闹钟（可选）+ 开机恢复，无任何多余权限
- **系统适配**：`minSdk 26`（Android 8.0）→ `targetSdk 36`（Android 16），适配 Android 15 / 16 / 17 强制边缘到边缘布局

## 自动更换实现说明

早期版本使用 WorkManager 24 小时周期任务，该机制由系统统一调度、受省电策略影响，很多机型（尤其国产 ROM）不会按时执行。当前版本改为：

1. **AlarmManager 精确闹钟**：每天凌晨 3:00 触发，熄屏也能执行
2. **开机恢复**：接收 `BOOT_COMPLETED` 广播，重启后自动重新注册，无需打开 App
3. **权限引导**：Android 12+ 首次打开若未授予"闹钟与提醒"权限，界面显示引导条，一键跳转系统设置授权；未授权时自动降级为每天 3 点附近执行
4. **手动兜底**：任何时刻可点"获取今日壁纸"立即强制更新

## 应用内设置

| 设置项 | 说明 |
| --- | --- |
| 设为壁纸 | 将当前浏览的历史壁纸设为主屏 + 锁屏 |
| 获取今日壁纸 | 立即联网获取必应今日 4K 壁纸并保存到历史 |
| ‹ › 前后选择 | 左右切换浏览历史壁纸；翻到底时自动联网加载更早一天（最多 5 天前，需联网） |
| 每日凌晨 3 点自动更换 | 开关；开启时注册精确闹钟，关闭时取消 |
| 闹钟权限引导 | Android 12+ 未授权时显示，点击跳转系统设置 |

## 权限清单

```
android.permission.INTERNET               # 联网获取壁纸
android.permission.SET_WALLPAPER          # 设置壁纸（普通权限，安装时自动授予）
android.permission.POST_NOTIFICATIONS     # 更新通知（Android 13+ 运行时请求）
android.permission.SCHEDULE_EXACT_ALARM   # 凌晨3点精确自动更换（Android 12+ 设置页授权，可选）
android.permission.RECEIVE_BOOT_COMPLETED # 开机恢复自动更换
# WorkManager 自动附带 WAKE_LOCK / ACCESS_NETWORK_STATE / FOREGROUND_SERVICE / DYNAMIC_RECEIVER 等系统级依赖权限
```

## 技术栈

- Kotlin + Jetpack（AppCompat / Material3 / WorkManager / ViewBinding）+ OkHttp
- 壁纸源：`https://www.bing.com/HPImageArchive.aspx?format=js&idx=N&n=1&mkt=zh-CN`（idx=0 今天…7 八天前），自动升级 UHD 3840×2160
- 每日调度：`AlarmManager.setExactAndAllowWhileIdle` 03:00 + `BOOT_COMPLETED` 恢复 + 无权限时 `setInexactRepeating` 降级；任务执行用 `OneTimeWorkRequest` + 网络约束 + 指数退避重试
- 壁纸历史：保存于 `filesDir/wallpapers/`，最近 30 张，超限自动清理

## 项目结构

```
DailyWallpaper/
├── app/
│   ├── build.gradle.kts          # 构建配置（minSdk 26 / targetSdk 36 / versionCode 6 / versionName 2.0.1）
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/dailywallpaper/
│       │   ├── MainActivity.kt        # 主界面：预览、前后选择、设为壁纸、获取今日、自动开关、权限引导
│       │   ├── WallpaperWorker.kt     # 任务：必应抓取→下载→历史→设置壁纸→通知
│       │   ├── WallpaperScheduler.kt  # AlarmManager 凌晨3点精确调度
│       │   ├── AlarmReceiver.kt       # 闹钟触发 + 开机恢复
│       │   ├── network/WallpaperApi.kt
│       │   ├── data/Prefs.kt
│       │   └── util/                  # WallpaperSetter（主屏+锁屏）/ WallpaperNotifier
│       └── res/                       # 布局、主题、自适应图标
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 构建

需要 JDK 17、Android SDK（`platforms;android-36` + `build-tools;37.0.0` 或 36.x）与 Gradle 8.13。

```bash
# 方式 A（推荐）：用 Android Studio 导入项目，自动匹配 Gradle 版本并同步
# 方式 B：本地已安装 Gradle 8.13，先在项目根目录创建 local.properties 写入 sdk.dir=<Android SDK 路径>
gradle assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

发布构建需要自己的签名配置（`signingConfigs.release`，密钥不纳入本仓库）。

## 说明

- 凌晨 3 点执行时手机需保持联网；离线时会重试直到成功
- 部分厂商（MIUI / ColorOS 等）有壁纸管理策略，设置后可在系统壁纸设置中确认
- 安卓 12+ 未授予"闹钟与提醒"权限时，将降级为每天 3 点前后执行（仍会自动更换，时间可能偏移）
- 壁纸历史保存在应用私有目录，卸载应用会一并清除
