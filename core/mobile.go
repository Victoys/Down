// Package libgopeed 是对 GopeedLab/gopeed 下载核心的薄封装，
// 供 Android 端通过 gomobile 生成的 AAR 调用。
//
// 工作方式：Go 侧在 127.0.0.1 的随机端口启动 gopeed 的 REST 服务，
// Android 端用 OkHttp 访问本地 HTTP 接口来增删查改下载任务。
// 所有协议（HTTP/HTTPS、BitTorrent、磁力、ed2k）均由 gopeed 核心负责。
package libgopeed

import (
	"encoding/json"
	"errors"

	"github.com/GopeedLab/gopeed/pkg/rest"
	"github.com/GopeedLab/gopeed/pkg/rest/model"
)

// Start 启动下载核心与本地 REST 服务。
// storageDir 用于存放任务数据库与配置（应传应用私有目录）。
// downloadDir 为默认下载目录（Android 上为 /storage/emulated/0/Download）。
// 返回 REST 服务监听的端口，失败时返回 error。
func Start(storageDir, downloadDir string) (int, error) {
	cfg, err := buildStartConfig(storageDir, downloadDir)
	if err != nil {
		return 0, err
	}

	port, err := rest.Start(cfg)
	if err != nil {
		return 0, err
	}
	// rest.Start 在 API 未启用时会返回 0 且不带错误，
	// 这里显式转成错误，避免上层拿着 0 端口去请求。
	if port == 0 {
		return 0, errors.New("gopeed REST 服务未启动（API 未启用）")
	}
	return port, nil
}

// Stop 停止下载核心。
func Stop() {
	rest.Stop()
}

// buildStartConfig 用 JSON 生成 StartConfig。
//
// 之所以不用结构体字面量：gopeed 的 StartConfig 字段在不同版本间会增删，
// 直接写 &model.StartConfig{ApiEnable: ...} 一旦字段变动就是编译错误
// （unknown field xxx in struct literal）。走 JSON 时未知字段会被静默忽略，
// 牺牲一点"字段名写错立刻报错"的即时反馈，换来跨版本的编译稳定性。
func buildStartConfig(storageDir, downloadDir string) (*model.StartConfig, error) {
	raw := map[string]any{
		"network":           "tcp",
		"address":           "127.0.0.1:0", // 端口交由系统分配
		"apiEnable":         true,
		"storage":           "bolt",
		"storageDir":        storageDir,
		// 只允许写这个目录，避免被恶意链接拿来写任意路径
		"whiteDownloadDirs": []string{downloadDir},
		"downloadConfig": map[string]any{
			"downloadDir": downloadDir,
			"maxRunning":  3,
		},
		// 下面两个字段没有 json tag，但 encoding/json 会按字段名匹配（大小写不敏感）
		// ProductionMode 只影响日志行为
		"ProductionMode": true,
		// NativeMode 保持 false：开启时 gopeed 会用持久化配置覆盖上面的 apiEnable，
		// 导致 REST 服务可能起不来
		"NativeMode": false,
	}

	data, err := json.Marshal(raw)
	if err != nil {
		return nil, err
	}

	cfg := &model.StartConfig{}
	if err := json.Unmarshal(data, cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
