# Upstream divergence log

Things Aniyomi relied on that Mihon has since changed, removed or replaced — recorded as they turn
up during the port, so the decision can be revisited deliberately instead of forgotten.

Each entry answers three questions: **what we use it for**, **what Mihon did**, and **whether their
reason is a better idea we should adopt**. The last one matters most: a removal upstream is often a
lesson, not an obstacle. Reintroducing something Mihon deleted for good reason means carrying a
mistake they already fixed.

## Status key

- **adopted** — we took Mihon's newer approach
- **carried** — we kept Aniyomi's version, with a reason
- **open** — needs a decision

---

## Adopted

### `PreferenceStore.getObject` → `getObjectFromString`

Used for preferences holding a serialised object: library sort mode, display mode.

A rename with an identical signature. We had written a replacement before noticing; it was deleted
and Mihon's used instead. A file we do not own is a file we do not maintain.

### Preferences: functions → properties

`libraryPreferences.displayMode()` became `libraryPreferences.displayMode`.

A plain modernisation with no behavioural change. Every call site in `:anime:services` now reads
the property. Our own anime preference classes still expose functions, so the two halves do not yet
read alike — worth doing, but it is a rename across our files, not an upstream obligation.

### `MemoColumnAdapter`

Stores a JSON object as bytes in the database.

Aniyomi wrote one; Mihon has since grown its own, behaviourally identical. Ours deleted, theirs
used.

### `source-api` as a multiplatform module

Mihon collapsed it to a plain Android library. `:anime:source-api` followed, since a common source
set cannot depend on an Android-only one. The one `expect`/`actual` pair collapsed to the Android
side, keeping the `PreferenceScreen` alias intact because extensions compile against that name.

---

### `SmallExtendedFloatingActionButton`

Aniyomi carried a copy in `presentation-core`. It has since graduated into Material 3 proper, and
Mihon uses the framework one. Ours now imports `androidx.compose.material3` — one fewer file to own.

### Shizuku: `newProcess` → a bound user service

Used by the anime extension installer to install an APK with elevated rights.

`Shizuku.newProcess` is private in the SDK version Mihon pins, so Aniyomi's installer could not have
been kept as it was. Mihon had already moved to binding an AIDL user service, which is the supported
approach. `ShizukuInstallerAnime` is now Mihon's implementation with the class name and base class
changed, binding the very `mihon.app.shizuku.ShellInterface` Mihon declares, under the same service
tag — so the two installers share one Shizuku user service rather than standing up a second.

A case where upstream's change was not an obstacle at all. It was the fix.

### `applyFilter` moved out of `tachiyomi.domain.entries`

Mihon collapsed its `entries` layer; the helper now lives in `tachiyomi.domain.manga.model`. A
one-line import change, and the second confirmation that the `entries`/`items` generalisation
Aniyomi built is gone upstream.

### `update_check_notification_download_in_progress` — deleted

Aniyomi showed a wordless "Downloading…" while a download's progress was still zero. Mihon dropped
that branch and the string with it, and always shows the percentage.

Adopted: 0% reads the same, and it is one fewer string to translate.

### `LibraryPreferences.ENTRY_*` → `MANGA_*`, `autoUpdateItemRestrictions` → `autoUpdateMangaRestrictions`

Not an upstream removal — Aniyomi had *renamed Mihon's own constants in place* to generalise them,
so they vanished the moment we stopped editing that file.

The stored key and values are identical, and the restriction set is a single preference both halves
read. So `AnimeLibraryPreferences` now declares `ANIME_NON_COMPLETED`, `ANIME_HAS_UNSEEN`,
`ANIME_NON_SEEN` and `ANIME_OUTSIDE_RELEASE_PERIOD` as **aliases of Mihon's constants** rather than
copies of the literals. Aliasing makes drift impossible: if upstream changes a value, ours changes
with it and the halves keep agreeing about what the preference means.

---

## Open

### `CategoryUpdate` — deleted by Mihon

Used by every category interactor to apply a partial update.

Mihon deleted the class. **What replaced it has not been established.** We kept our own copy — as
`AnimeCategoryUpdate`, in a package of ours — so the port could proceed, but if Mihon replaced it
with something better shaped, ours should follow.

*To check: how Mihon's category interactors apply partial updates now.*

### `kotlinx-collections-immutable` — dropped by Mihon

Our ported components take `ImmutableList` and `persistentListOf` in composable parameters. Mihon
now uses it in only two files and dropped the dependency from its catalogues; we added it to ours
to keep the port mechanical.

The likely reason is Compose strong skipping, which made immutable collection types largely
unnecessary for recomposition stability. If so this is a genuine simplification: plain `List`
everywhere, one dependency fewer, and no behaviour change.

*To check: whether strong skipping is on in Mihon's Compose configuration. If it is, drop the
dependency and use `List`.*

### SQLDelight: Mihon generates async, we still generate sync

Mihon's `:data` sets `generateAsync = true` and drives it with `AndroidxSqliteDriver` over bundled
SQLite. That driver accepts **only** an async schema, so `:anime:data` — which still generates a
synchronous one, as Aniyomi's did — cannot use it. The anime database is on SQLDelight's own
`AndroidSqliteDriver` for now, pinned to the version Mihon's catalogue names so the two cannot
drift apart.

This is worth following: bundled SQLite means one behaviour across every Android version, and it
drops Aniyomi's requery dependency, which Mihon has already removed.

The work looks contained. Every query in `:anime:data` runs through `AndroidAnimeDatabaseHandler`,
which is the only place `executeAsList`/`executeAsOne` appear; the repositories call
`handler.awaitList { … }` and would not change shape. It was not done here because it is a change
to the data layer, not to dependency injection, and it deserves its own verification.

*To do: turn on `generateAsync` in `:anime:data`, convert the handler, move to the androidx driver.*

### `GetLibraryManga` — moved

Needed by the unified library, which reads both halves at once. Mihon moved it out of
`tachiyomi.domain.entries.manga.interactor`; it now lives under `tachiyomi.domain.manga`, and the
`entries` layer appears to be gone entirely.

The unified library interactor is parked until the screen that uses it is built, so this is
unresolved by design rather than by neglect.

---

## Not upstream changes — our own boundary violations

Recorded here because they surfaced the same way, as compile errors during the port, but the cause
is different: Aniyomi had added these *inside* Mihon's files.

| Thing | Now |
| --- | --- |
| `Category.hidden` | `animato.domain.category.AnimeCategory` — anime categories were always a separate table with a separate id space |
| `EntryCover` | `animato.domain.entries.EntryCover` |
| anime column adapters in `tachiyomi.data` | `animato.data` — never put a class in a package upstream owns, even when it does not collide today |
| `DownloadAction` | Mihon has its own in `MangaScreenConstants`; ours lives in `animato.ui.entries` and the two are independent |
| `ExtensionUpdateNotifier(names, anime = true)` | `animato.anime.services.AnimeExtensionUpdateNotifier` — a flag that only chose which screen to open is a class of ours instead |
| `SourcePreferences.animeExtensionUpdatesCount` | `aniyomi.domain.source.service.AnimeSourcePreferences` |
| `Notifications.CHANNEL_HTTP_SERVER` / `ID_HTTP_SERVER` | `animato.anime.services.AnimeNotifications`, values unchanged |
| `Constants.SHORTCUT_ANIME*` | `animato.anime.services.AnimeConstants`, action strings unchanged |
| `MainActivity.startHttpServerService` | `HttpServerService.start` — it starts a service and waits on a flow; nothing in it touched an activity |
| anime components in Mihon's `AndroidManifest.xml` | `:anime:services`' own manifest, merged in by the build |

---

## Bugs inherited from Aniyomi, fixed rather than carried

Found while porting, all in code that is now ours. Each was wrong on its own terms, not a
difference of opinion with upstream.

| Where | What | Fix |
| --- | --- | --- |
| `AnimeLibraryUpdateNotifier` | the Download action passed `Notifications.ID_NEW_CHAPTERS`, the *manga* group, so tapping it dismissed the manga group and left the anime notification showing | uses `AnimeNotifications.ID_NEW_EPISODES`, agreeing with the two actions beside it |
| `ExtensionUpdateNotifier` | anime and manga extension updates shared `ID_UPDATES_TO_EXTS`, so an anime notification replaced a pending manga one and the user lost that list | anime updates post under `-403`, beside Mihon's `-401`/`-402` rather than on top |
| `NotificationReceiver` | two `openEpisodePendingActivity` overloads differing only in `Episode` vs `Int`, which is how a call site came to pass a notification id where an episode was meant | distinct names: `openEpisodePendingActivity` and `openAnimeEntryPendingActivity` |
