// Command validate loads each Xray configuration given on the command line with Xray's own
// loader and builds a core instance from it, without starting it — the check that the JSON the
// app writes (animato.anime.net.xray.XrayConfig) is a configuration Xray accepts. Not shipped.
package main

import (
	"fmt"
	"os"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
	_ "github.com/xtls/xray-core/main/distro/all"
)

func main() {
	failed := false
	for _, path := range os.Args[1:] {
		file, err := os.Open(path)
		if err != nil {
			fmt.Println("FAIL", path, err)
			failed = true
			continue
		}
		config, err := serial.LoadJSONConfig(file)
		_ = file.Close()
		if err == nil {
			var instance *core.Instance
			instance, err = core.New(config)
			if instance != nil {
				_ = instance.Close()
			}
		}
		if err != nil {
			fmt.Println("FAIL", path, err)
			failed = true
		} else {
			fmt.Println("OK  ", path)
		}
	}
	if failed {
		os.Exit(1)
	}
}
