# 下载 —— 安卓极简下载器（基于 gopeed 核心）

基于 [GopeedLab/gopeed](https://github.com/GopeedLab/gopeed) 的 Go 下载核心，配一个极简 Kotlin 界面。
协议能力全部来自 gopeed 核心：**HTTP / HTTPS、BitTorrent 种子、磁力链接、ed2k 电驴链接**。

## 界面

- 底部两个标签：**下载** / **已完成**，左右滑动也可切换
- 左页：一个输入框 + 一个下载按钮（下方一行是任务状态与进度）
- 右页：已完成文件列表，点击可用系统其他应用打开
- 文件默认保存到 `/storage/emulated/0/Download`

## 工作原理

```
Kotlin UI  →  OkHttp(127.0.0.1:随机端口)  →  Go 核心(gopeed)  →  写盘
```

Go 侧用 `gomobile bind` 编成 AAR（`core/mobile.go`），启动 gopeed 的 REST 服务；
Android 侧通过本地 HTTP 调 `/api/v1/tasks` 等接口创建和查询任务。
这也是 gopeed 官方移动端的做法（见其 `bind/mobile/main.go`）。

## 用 GitHub Actions 出 APK

1. 把整个目录推到你的 GitHub 仓库
2. 打开 **Actions → Build APK → Run workflow**（或 push 到 main 自动触发）
3. 跑完在 Artifacts 里下载 `download-apk-debug`，里面的 `app-debug.apk` 直接装

> 首次构建约 15–30 分钟，时间主要花在编译 Go 核心（gopeed 依赖很多模块）。

## 目录结构

| 路径 | 说明 |
|---|---|
| `core/mobile.go` | Go 薄封装，供 gomobile 生成 AAR |
| `app/src/main/java/dev/victoys/down/GopeedService.kt` | 前台服务，启动 Go 核心 |
| `app/src/main/java/dev/victoys/down/GopeedClient.kt` | 本地 REST 客户端 |
| `app/src/main/java/dev/victoys/down/DownloadFragment.kt` | 左页 |
| `app/src/main/java/dev/victoys/down/FilesFragment.kt` | 右页 |
| `.github/workflows/build-apk.yml` | 自动构建 APK |

## 权限说明

- **所有文件访问权限**：Go 核心按真实路径写 `/storage/emulated/0/Download`，
  Android 11+ 必须授予，首次启动会跳到设置页
- 通知 + 前台服务：保证后台下载不被系统掐掉

## 常见调整

| 想改什么 | 改哪里 |
|---|---|
| 兼容 32 位设备 | `app/build.gradle` 的 `abiFilters` 加 `'armeabi-v7a'` |
| NDK 版本 | workflow 里 `ndk;26.1.10909125` |
| Go 版本 | workflow 里 `go-version: '1.25'` |
| 并发任务数 | `core/mobile.go` 的 `MaxRunning` |
| 下载目录 | `GopeedService.DOWNLOAD_DIR` |

## 需要你知道的几点

- 本项目代码按 gopeed 源码接口编写；构建环境（Go / NDK / Android SDK）只在 GitHub Actions 上跑，
  首次 Actions 若报编译错，按日志里的行号微调即可（`core/mobile.go` 是最可能需要随 gopeed 版本调整的地方）。
- 冷门 BT 资源速度取决于做种节点热度，开源下载器没有迅雷的离线服务器，直链下载才能跑满带宽。
- 核心启动需要几秒，刚打开 App 时状态栏会显示"正在启动下载核心…"。
