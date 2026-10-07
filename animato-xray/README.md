# animato-xray

The built-in proxy's engine: [Xray-core](https://github.com/XTLS/Xray-core) as an Android library,
with a three-function bridge in `libxray/` — start from a JSON configuration, stop, report the
version. Built with gomobile into `build/libxray.aar`, which `:animato-app` depends on.

Everything else lives in Kotlin:

- `anime/services/.../net/xray/XrayLink.kt` reads share links (vless, vmess, trojan, ss).
- `anime/services/.../net/xray/XrayConfig.kt` turns one into an Xray configuration: SOCKS and HTTP
  inbounds on `127.0.0.1`, the server as the outbound, private addresses direct.
- `animato-app/.../xray/XrayController.kt` starts and stops the core as the proxy settings change.

The app reaches the internet through the local HTTP inbound because `ProxyPreferences.proxy()`
answers with it — the same path a manually configured proxy takes. Nothing is system-wide: no
`VpnService`, no TUN, no effect on other apps.

## Building

```sh
./animato-xray/build-aar.sh
```

Needs Go (the module pins its toolchain and Go downloads it) and the Android NDK. CI runs the same
script through `.github/actions/xray-aar`, cached on the inputs.

`cmd/validate` loads Xray configurations with Xray's own loader, without starting anything. It is
how the configurations `XrayConfig` writes are checked against the real parser:

```sh
XRAY_CONFIG_DUMP=/tmp/xray ./gradlew :anime:services:testDebugUnitTest --tests 'animato.anime.net.xray.*'
(cd animato-xray && go run ./cmd/validate /tmp/xray/*.json)
```

## Licence

Xray-core is under the Mozilla Public License 2.0. It is used **unmodified**, as a Go module
dependency at the version `go.mod` names; its source is available from the upstream repository at
that version. MPL-2.0 is file-level copyleft: it covers Xray's own files, not the code that uses
them, so the bridge here and the app around it stay under this repository's licence. If Xray's
files are ever patched, those patched files must be published under MPL-2.0.

Android's common Xray wrapper, AndroidLibXrayLite, is LGPL-3.0 and is deliberately not used.
