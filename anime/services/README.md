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

## What is left, in the order worth doing it

1. ~~`:anime:source-local`~~ — **done**, the module exists and builds.
2. **Anime drawables** (`ic_ani*`) and `util/size` — small, mechanical.
3. **More notification helpers** — `openAnimeDownloadManagerPendingActivity`,
   `openAnimeEntryPendingActivity`. Same story as the constants: Aniyomi put them inside Mihon's
   `NotificationHandler`. They belong next to `AnimeNotifications`.
4. **`RemoteAnimeSeasonUpdate`, `RemoteAnimeEpisodeUpdate`, `toSAnime`, `deleteFromCache`** —
   scattered helpers, resolve as their owners land.

## The lesson to carry

Every earlier port shrank as it went. This one grew: each fix uncovered another unported
dependency, because the services layer is where anime code stops being self-contained and starts
calling the app. That is worth knowing before estimating the layers above it.
