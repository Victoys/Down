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

## 关于 Android 16

当前配置是 `compileSdk 35 / targetSdk 35`，这是 AGP 8.7.3 最稳妥的组合，
在 Android 16 设备上正常运行，也满足 Google Play 对 targetSdk 的要求。

如果你一定要 `targetSdk 36`，需要同时升级构建链（AGP、Gradle、Kotlin 一起动）：
- `app/build.gradle`：`compileSdk 36`、`targetSdk 36`
- 根 `build.gradle`：AGP 升到 8.10+，Kotlin 插件相应升级
- workflow：`GRADLE_VERSION` 与 `platforms;android-36`、`build-tools;36.0.0`

## 构建卡在 SDK 许可证确认？

日志里出现 `7 of 8 SDK package licenses not accepted` / `Accept? (y/N)` 是
`sdkmanager --licenses` 在等人工输入。本仓库的 workflow 已用两步规避：
1. 预先写入 `$ANDROID_HOME/licenses/` 下的许可证文件
2. `yes | timeout 600 sdkmanager --licenses` 兜底

因此没有使用 `android-actions/setup-android`，那个 action 会自己调一次
`sdkmanager --licenses`，在部分镜像上会卡住。

## 构建报 `gomobile: missing golang.org/x/mobile dependency`？

gomobile 从某个版本起要求**被打包的模块自己显式依赖** `golang.org/x/mobile`，
只在本机 `go install` 装了 gomobile 不够。修复方式是加一条 tool 指令：

```bash
cd core
go mod tidy
go get -tool golang.org/x/mobile/cmd/gobind
go mod tidy
```

本仓库的 workflow 已经包含这两步。若你本地构建，手动执行上面命令即可。

## 构建报 `no exported names in the package "-v"`？

`gomobile bind` 不支持 `-v` 参数，它会把 `-v` 当成**要打包的包名**去解析，
于是报出这个莫名其妙的错。去掉 `-v` 即可：

```bash
gomobile bind -target=android -androidapi 24 -o ../app/libs/gopeed-core.aar .
```

## 报 `unknown field ApiEnable in struct literal of type model.StartConfig`？

原因：`go mod tidy` 默认挑 gopeed 的**最新 tag**（如 v1.9.x），
而那一版的 `pkg/rest/model.StartConfig` 字段和 main 分支不一样，
结构体字面量里写 `ApiEnable` 就编译不过。

两个改动一起解决：

1. **锁版本**：workflow 里 `go get github.com/GopeedLab/gopeed@main`，
   强制用 main 分支（本项目代码就是按 main 写的）。
2. **改用 JSON 构造配置**：`core/mobile.go` 不再写结构体字面量，
   而是 `json.Marshal(map) → json.Unmarshal(&cfg)`。
   gopeed 后续增删字段时，未知字段会被忽略，不会直接编译失败。

另外 `Start()` 里加了保护：`rest.Start` 在 API 未启用时会返回端口 0 且不带错误，
这里显式转成 error，避免上层拿着 0 端口去请求。

> 注意 `NativeMode` 必须保持 false。开启时 gopeed 会用持久化配置覆盖 `apiEnable`，
> REST 服务可能起不来。
