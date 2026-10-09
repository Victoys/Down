// Package libgopeed 是对 GopeedLab/gopeed 下载核心的薄封装，
// 供 Android 端通过 gomobile 生成的 AAR 调用。
//
// 工作方式：Go 侧在 127.0.0.1 的随机端口启动 gopeed 的 REST 服务，
// Android 端用 OkHttp 访问本地 HTTP 接口来增删查改下载任务。
// 所有协议（HTTP/HTTPS、BitTorrent、磁力、ed2k）均由 gopeed 核心负责。
package libgopeed

import (
	"github.com/GopeedLab/gopeed/pkg/base"
	"github.com/GopeedLab/gopeed/pkg/rest"
	"github.com/GopeedLab/gopeed/pkg/rest/model"
)

// Start 启动下载核心与本地 REST 服务。
// storageDir 用于存放任务数据库与配置（应传应用私有目录）。
// downloadDir 为默认下载目录（Android 上为 /storage/emulated/0/Download）。
// 返回 REST 服务监听的端口，失败时返回 error。
func Start(storageDir, downloadDir string) (int, error) {
	apiEnable := true
	cfg := &model.StartConfig{
		Network:           "tcp",
		Address:           "127.0.0.1:0", // 端口交由系统分配
		ApiEnable:         &apiEnable,
		Storage:           model.StorageBolt,
		StorageDir:        storageDir,
		WhiteDownloadDirs: []string{downloadDir},
		DownloadConfig: &base.DownloaderStoreConfig{
			DownloadDir: downloadDir,
			MaxRunning:  3,
		},
		ProductionMode: true,
	}
	return rest.Start(cfg)
}

// Stop 停止下载核心。
func Stop() {
	rest.Stop()
}
