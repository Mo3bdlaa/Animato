# `:anime:services`

The layer between the anime database and the screens: extension management, downloads, the library
update job, notifications and the torrent and HTTP servers.

It compiles, it is registered in `settings.gradle.kts`, and `:animato-app` depends on it, so its
components ship in the APK.

## What it took

Compile errors went **282 → 254 → 210 → 184 → 153 → 133 → 111 → 98 → 64 → 38 → 16 → 0**.

Most rounds were mechanical. The ones that were not are the interesting record:

### The receiver

`AnimeNotificationReceiver` is a `BroadcastReceiver` of our own handling the anime notification
actions — pause, resume and clear downloads, cancel the library update, mark seen, download
episodes. Aniyomi added these cases to Mihon's receiver; a receiver is declared in a manifest and
dispatches on intent action, so ours simply coexists and Mihon's never learns it exists.

Action strings and extra keys are byte-identical to Aniyomi's, including the extras that carry
anime ids under Mihon's *manga* key names, so notifications posted by an older build still resolve.

Aniyomi had two `openEpisodePendingActivity` overloads differing only in whether the last parameter
was an `Episode` or an `Int` group id. That is how a call site came to pass a notification id where
an episode was meant. The two now have distinct names.

### The player seam

`AnimeDownloader` imported `EpisodeLoader` and `HosterLoader` from `ui.player.loader` — a background
service depending on the player. That requirement is now stated as
`animato.anime.services.download.EpisodeVideoResolver`, an interface here that the player will
implement and bind. The downloader depends on what it needs; nothing below the line knows the
player exists.

`MainActivity.startHttpServerService` was the same inversion in miniature — a service-start helper
parked on an activity, which the downloader then had to reach up into. `HttpServerService` moved
into this module and the helper became `HttpServerService.start`.

### Where the ported interactors went

`SetSeenStatus`, `SyncEpisodesWithSource`, `SyncSeasonsWithSource`, `UpdateAnime`,
`TrustAnimeExtension` and `DeleteEpisodeDownload` are here rather than in `:anime:domain`, because
Mihon keeps its own equivalents in `:app`: they need the download manager and app-level
preferences, which a domain module has no business seeing.

## What is deliberately not here

- **`EpisodeFilter`'s `List<EpisodeList.Item>` overload** — it filters what a screen is about to
  draw and takes a screen model. It moves with the episode screen in phase 6.
- **Tab-bar and launcher drawables** — `ic_animelibrary_*` and `ic_ani_monochrome_launcher` came
  across with the port and were removed. The broken selector that failed the resource link is the
  argument: they were copied without the animated vectors they reference, and nothing in this layer
  draws a tab bar. They return with the UI.

## Runtime state

The Injekt modules now exist, in `:animato-app` under `animato.di`: `AnimePreferenceModule`,
`AnimeAppModule` and `AnimeDomainModule`, bootstrapped by `AnimeInjektInitializer`. Everything this
module resolves is bound, with one deliberate exception.

`EpisodeVideoResolver` is implemented by `:anime:player` and bound in `AnimeAppModule`, so nothing
this module resolves is unbound. `AnimeDownloader` still injects it lazily rather than taking it as
a constructor parameter — the implementation lives above this layer, and resolving it eagerly would
tie constructing the download manager to the player existing.
