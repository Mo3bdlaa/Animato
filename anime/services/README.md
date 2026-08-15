# `:anime:services` — work in progress

Not registered in `settings.gradle.kts`, so it is not part of the build yet. The files are here
because they are ported and the analysis behind them is worth keeping; wiring it in before it
compiles would only make CI red for everyone.

## Where it stands

31 files ported: the anime extension manager, download manager, library update job, source manager
and torrent service. Compile errors went 282 → 254 → 210 across three rounds, and 65 distinct
symbols remain unresolved.

## What is already settled

- `InstallStep` and `ExtensionUpdateNotifier` still exist upstream, only at new paths
- Notification ids and channels Aniyomi had added inside Mihon's `Notifications` object now live
  in `animato.anime.services.AnimeNotifications`, numbers unchanged so installs keep their channels
- `UpdateAnimeFromRemote` and the torrent core are ported
- Compose artifacts arrive without versions through `:app`, so the Compose BOM has to be declared

## What is left

Errors went 282 → 254 → 210 → 184 → 141 over five rounds. Each round is mechanical; the count
falls because the missing pieces are small and specific. One is not.

### The receiver — done

`AnimeNotificationReceiver` now exists. It is a `BroadcastReceiver` of our own handling the anime
actions — pause, resume and clear downloads, cancel the library update, mark seen, download
episodes — with action strings and extra keys byte-identical to Aniyomi's, so notifications posted
by an older build still resolve. Mihon's receiver keeps handling Mihon's actions and never learns
this one exists.

Two decisions inside it worth knowing:

- The extras keep Mihon's manga key names (`EXTRA_MANGA_ID`, `EXTRA_CHAPTER_URL`) because Aniyomi
  reused them for anime. Renaming would orphan already-posted notifications.
- Opening an episode routes through the main activity rather than starting the player directly.
  The player sits above this module, and a service holding a hard reference to it would invert the
  layering. Navigation resolves it once the player lands.

It still needs registering in a manifest — `:animato:app`'s, not Mihon's.

### The rest, all mechanical

- `StorageUtil` (`size`) and `AnimeBackgroundCache` — single files in `util/`
- `Constants` — the shortcut action ids
- `formatEpisodeNumber` — already ported into `:animato:ui-kit`, just needs pointing at
- `ic_ani*` drawables — copied in, but the code still imports `eu.kanade.tachiyomi.R` rather than
  the library's own `R`
- `deleteFromCache`, `toSAnime`, `await` on a few interactors — resolve as their owners land

## The lesson to carry

Every earlier port shrank as it went. This one grew first and only then shrank. Each fix uncovered
another unported dependency, because the services layer is where anime code stops being
self-contained and starts calling the app — and where Aniyomi's edits to Mihon's files are
thickest. That is worth knowing before estimating the layers above it.
