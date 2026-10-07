// Package libxray is Animato's bridge to Xray-core: start one Xray instance from a JSON
// configuration, stop it, and say which version is inside.
//
// It is deliberately this small. Everything that knows about share links, ports and routing lives
// in Kotlin (animato.anime.net.xray), where it can be tested and changed without a Go build; this
// side only has to turn a configuration into a running core. Xray-core is MPL-2.0 and is used
// unmodified, as a library — see animato-xray/README.md.
package libxray

import (
	"errors"
	"strings"
	"sync"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"

	// Only what a client of one server needs, registered by import. `main/distro/all` would also
	// bring the command line, the gRPC API, statistics, WireGuard, the server-side protocols and
	// every config format — about a third of the library, in an APK that stores it uncompressed.
	_ "github.com/xtls/xray-core/app/dispatcher"
	_ "github.com/xtls/xray-core/app/dns"
	_ "github.com/xtls/xray-core/app/log"
	_ "github.com/xtls/xray-core/app/policy"
	_ "github.com/xtls/xray-core/app/proxyman/inbound"
	_ "github.com/xtls/xray-core/app/proxyman/outbound"
	_ "github.com/xtls/xray-core/app/router"

	// The two local inbounds, and the outbounds: the four protocols links come in, plus direct.
	_ "github.com/xtls/xray-core/proxy/blackhole"
	_ "github.com/xtls/xray-core/proxy/freedom"
	_ "github.com/xtls/xray-core/proxy/http"
	_ "github.com/xtls/xray-core/proxy/shadowsocks"
	_ "github.com/xtls/xray-core/proxy/socks"
	_ "github.com/xtls/xray-core/proxy/trojan"
	_ "github.com/xtls/xray-core/proxy/vless/outbound"
	_ "github.com/xtls/xray-core/proxy/vmess/outbound"

	// Every transport and security layer a share link can name.
	_ "github.com/xtls/xray-core/transport/internet/grpc"
	_ "github.com/xtls/xray-core/transport/internet/headers/http"
	_ "github.com/xtls/xray-core/transport/internet/headers/noop"
	_ "github.com/xtls/xray-core/transport/internet/httpupgrade"
	_ "github.com/xtls/xray-core/transport/internet/kcp"
	_ "github.com/xtls/xray-core/transport/internet/reality"
	_ "github.com/xtls/xray-core/transport/internet/splithttp"
	_ "github.com/xtls/xray-core/transport/internet/tcp"
	_ "github.com/xtls/xray-core/transport/internet/tls"
	_ "github.com/xtls/xray-core/transport/internet/udp"
	_ "github.com/xtls/xray-core/transport/internet/websocket"
)

var (
	mu       sync.Mutex
	instance *core.Instance
)

// Start runs Xray with the given JSON configuration, replacing any instance already running.
// It returns once the inbounds are listening, or with the reason they are not.
func Start(configJSON string) error {
	mu.Lock()
	defer mu.Unlock()

	stopLocked()

	config, err := serial.LoadJSONConfig(strings.NewReader(configJSON))
	if err != nil {
		return errors.New("invalid configuration: " + err.Error())
	}
	server, err := core.New(config)
	if err != nil {
		return errors.New("could not create Xray: " + err.Error())
	}
	if err := server.Start(); err != nil {
		_ = server.Close()
		return errors.New("could not start Xray: " + err.Error())
	}
	instance = server
	return nil
}

// Stop shuts the running instance down, if there is one.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	stopLocked()
}

// IsRunning says whether an instance is currently up.
func IsRunning() bool {
	mu.Lock()
	defer mu.Unlock()
	return instance != nil
}

// Version is the Xray-core version compiled in.
func Version() string {
	return core.Version()
}

func stopLocked() {
	if instance != nil {
		_ = instance.Close()
		instance = nil
	}
}
