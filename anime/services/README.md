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

### The one that needs designing, not copying

`NotificationReceiver`. Aniyomi added the anime notification *actions* — mark seen, download next,
open episode — inside Mihon's own `BroadcastReceiver`, along with `openEpisodePendingActivity` and
`openAnimeEntryPendingActivity`. There is no version of copying that fixes this: a receiver is
registered in the manifest and dispatches on intent actions, so ours has to be a receiver of our
own that handles the anime actions and leaves Mihon's alone.

Do this first. It is the only remaining item with a real decision in it.

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
