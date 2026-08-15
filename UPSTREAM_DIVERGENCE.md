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

### Legacy ORM models — kept by Mihon

Aniyomi's `eu.kanade.tachiyomi.data.database.models.anime.{Episode,EpisodeImpl,AnimeTrack,
AnimeTrackImpl}` look like leftovers, and `Episode.kt` carries a "remove when all deps are
migrated" comment.

They are **not** leftovers to delete: Mihon still keeps its own `Chapter`/`ChapterImpl` in the same
place. Ported as they are, and only the player uses them.

### `requery` — dropped by Mihon

Aniyomi opened its databases through `RequerySQLiteOpenHelperFactory`, a bundled SQLite build that
existed to get consistent behaviour on old Android versions.

Mihon dropped it in favour of `androidx.sqlite:sqlite-bundled`, which does the same job as a
first-party library. Our anime database no longer references requery either — see the SQLDelight
entry below for what it does use, and why that is not yet Mihon's driver.

### `MainActivity` — copied, and two of its features left out

`animato.app.MainActivity` is an adapted copy of Mihon's. It has to be: Mihon selects colours from
an `AppTheme` **enum** through a **private** `colorSchemes` map over an `internal BaseColorScheme`,
none of which another module can extend, so the Animato palette can only be applied *above* Mihon's
screens — by owning the root composable. ARCHITECTURE.md sets out why nothing else unlocks it.

It still hosts Mihon's own `HomeScreen`, so the app behaves exactly as before and merely looks like
Animato. Two things from the original are deliberately gone:

- **`CheckForUpdates()`** — `AppUpdateChecker` reads releases from `mihonapp/mihon`. Calling it
  would offer our users Mihon's APK, which they cannot install: different package, different signing
  key. *To do: an Animato updater pointed at this repository's own releases, which the alpha
  workflow now produces.*
- **`ShowDonationCampaign()`** — pushes Mihon's `SupportUsScreen`. Soliciting donations to another
  project from a rebranded app is not ours to do.

Three further differences are forced rather than chosen:

- `NotificationReceiver.dismissNotification` is `internal`, so tapping a notification dismisses it
  through our `AnimeNotifications.dismiss`, which implements the same group-summary rule.
- `ExtensionApi` is `internal`; it was only reachable from inside `CheckForUpdates`, which is gone.
- The splash `ready` flag is set when the navigator composes, rather than by each tab. Mihon's tabs
  set it with `(context as? MainActivity)?.ready = true`, a cast against *their* class that ours
  cannot satisfy, since their `MainActivity` is final. Left alone the splash would hang for its
  five-second ceiling on every cold start.

*To do: re-read Mihon's `MainActivity` on each upstream sync. It is the one file of ours that tracks
theirs closely enough for their changes to matter.*

### Mihon's `MainActivity` is replaced by an `activity-alias`

Nine places name `eu.kanade.tachiyomi.ui.main.MainActivity` as an explicit component: eight
`Intent(context, MainActivity::class.java)` constructions across notifications, deep links, the
reader, the OAuth callback and the crash handler, plus every `<intent android:targetClass>` in
`@xml/shortcuts`. None are ours to change.

So the activity is removed with `tools:node="remove"` and an `<activity-alias>` of the same name
points at ours. Aliases resolve by component name, and nothing requires that name to match a class,
so all nine keep working — with their actions and extras intact, which is what our copy of
`handleIntentAction` reads.

The alias carries no intent-filter on purpose: one with a LAUNCHER category would put a second icon
in the launcher.

---

## Open

### Simkl is not among the ported trackers

`AniChartApi` asked three trackers for an anime's next air time: AniList, MyAnimeList and Simkl.
The first two are Mihon's and came across unchanged. Simkl is Aniyomi's own — nine files under
`data/track/simkl` — and none of the anime-only trackers have been ported yet, so that branch is
absent rather than stubbed. Airing times still work through the other two.

The Simkl calendar path comes back with the tracker, not before it.

### Our preferences are functions; Mihon's are now properties

Mihon has moved its preference accessors from functions to properties:

```kotlin
preferences.enabledLanguages()        // Aniyomi, and every anime module here
preferences.enabledLanguages          // Mihon 0.20.4
```

Ours did not move with them. **158 accessors across 11 classes** — `AnimeLibraryPreferences` (47),
`PlayerPreferences` (36), `SubtitlePreferences` (21), `GesturePreferences` (13), and the rest — are
still functions, so the codebase reads both ways depending on which side of the line a preference
came from.

This is cosmetic, which is exactly why it is easy to leave and easy to trip over. Nothing fails to
compile, so it will not announce itself; someone writing an anime screen will simply guess wrong
about half the time, and the guess is silent until they build.

Converting is mechanical — `fun x() = store.getBoolean(...)` becomes `val x: Preference<Boolean> =
store.getBoolean(...)` — but it touches every call site in `:anime:ui`, `:anime:player` and
`:anime:services`. Doing it while `:anime:ui` still has hundreds of compile errors would mix a
rename into a port and make both harder to review.

*To do: after 6b compiles, convert our preference classes to properties in one pass. Do it as its
own commit, touching nothing else, so the diff is reviewable as a rename.*

### `:app` must not minify itself — `isMinifyEnabled` is `false` here

Upstream sets `isMinifyEnabled = true` on `:app`'s release build type. That is right for an
application and wrong for a library, which is what `:app` is here.

A library produces two outputs: a **compile** jar consumers build against, and the **runtime**
classes that reach the APK. Minifying the library makes them disagree, because R8 optimises on the
assumption that it can see every caller — true when `:app` was the application, false now. Measured
on the release build: **57,438 method signatures at compile time, 38,721 at runtime.** Among the
rewrites:

```
registerSecureActivity(AppCompatActivity)  ->  registerSecureActivity(BaseActivity)
```

R8 saw only in-library callers passing a `BaseActivity` and narrowed the parameter. Everything
compiled; `v0.1.0-alpha.2` died with `NoSuchMethodError` before drawing a frame. Every Mihon symbol
our code calls was exposed to the same rewriting — that one was merely the first to be reached.

`proguardFiles` became `consumerProguardFiles` in the same change, so Mihon's keep rules reach
whoever does minify. `proguardFiles` on a library configures only the library's own R8 run.

**This is the entry to re-check on every upstream sync.** A merge that restores `isMinifyEnabled =
true` reintroduces a crash that compiles cleanly. `.github/check-library-abi.sh` runs in
`quick_check.yml` and fails the build if the shipped API ever diverges again; it has been tested
against the broken configuration as well as the fixed one.

*To do: enable minification on `:animato-app` instead, where R8 sees the whole program. Mihon's keep
rules already arrive; ours for the anime modules do not yet exist. See
[docs/APK_SIZE.md](docs/APK_SIZE.md) — the unshrunk dex is 26.2 MB of the 127 MB APK, its largest
single item.*


### `CategoryUpdate` — replaced by named methods

Aniyomi's category interactors applied a partial update by building a `CategoryUpdate` with every
field nullable. Mihon deleted the class and gave the repository explicit methods instead:

```kotlin
suspend fun updateName(categoryId: Long, name: String)
suspend fun updateFlags(categoryId: Long, flags: Long)
suspend fun updateAllFlags(flags: Long?)
suspend fun updateAllOrders(orderedIds: List<Long>)
```

**Their version is better and we should follow it.** An all-nullable update object can express
things that are not operations — update nothing, or rename and reorder and reflag at once — so every
implementation has to defend against combinations no caller intends. A named method says what
changes, and the compiler enforces that the caller supplies it.

We kept `AnimeCategoryUpdate` to get the port moving. It is now a deliberate carry, not an unknown.

*To do: give `AnimeCategoryRepository` the same four methods and delete `AnimeCategoryUpdate`.*

### `kotlinx-collections-immutable` — dropped by Mihon

Our ported components take `ImmutableList` and `persistentListOf` in composable parameters. Mihon
uses it in two files and dropped the dependency from its catalogues; we added it to ours to keep the
port mechanical.

**Resolved: the reason is Compose strong skipping, and it applies to us too.** Strong skipping has
been on by default since the Compose compiler shipped inside Kotlin 2.0, and this repository is on
Kotlin 2.4.10 with no setting that turns it off. Under strong skipping, composables skip
recomposition when their unstable parameters are referentially equal, which is what wrapping a
`List` in an immutable type used to buy.

So the immutable types are no longer earning anything, and the dependency is ours alone to carry.

*To do: use plain `List` in our composable parameters and drop `animato.kotlinx.immutables`.*

### Voyager `ScreenModel` → Mihon's `StateViewModel`

Aniyomi's player settings screens used Voyager's `StateScreenModel`, `rememberScreenModel`,
`screenModelScope`.

Mihon has dropped Voyager screen models entirely — the artifact is not even in its catalogues any
more — in favour of `mihon.core.viewmodel.StateViewModel`, a plain AndroidX `ViewModel` holding a
`MutableStateFlow`. The shapes line up almost exactly: `mutableState` keeps its name,
`screenModelScope` becomes `viewModelScope`, `rememberScreenModel { }` becomes `viewModel { }`.

Adopted. An AndroidX `ViewModel` is the platform's own answer to surviving configuration changes,
and it is one fewer third-party abstraction between a screen and its state.

### `convertEpochMillisZone` and the date helpers → `kotlinx.datetime`

Mihon moved `eu.kanade.tachiyomi.util.lang`'s date helpers from `java.time` to `kotlinx.datetime`,
so `convertEpochMillisZone` now takes `kotlinx.datetime.TimeZone` rather than `ZoneId`, and
`toRelativeString` hangs off `kotlinx.datetime.LocalDate`. Mihon also grew `Long.toLocalDate()`,
which replaces building a `LocalDate` from an `Instant` and a zone by hand.

Adopted, and the call sites got shorter for it.

### `Location.Pictures` — constructor made private

Mihon put a `create()` factory in front of it. A one-word change at each call site.

### SQLDelight: Mihon generates async, we still generate sync

Mihon's `:data` sets `generateAsync = true` and drives it with `AndroidxSqliteDriver` over bundled
SQLite. That driver accepts **only** an async schema — checked against the artifact, not assumed —
so `:anime:data`, which still generates a synchronous one as Aniyomi's did, cannot use it. The
anime database runs on SQLDelight's own `AndroidSqliteDriver`, pinned to the version Mihon's
catalogue names so the two cannot drift apart.

Worth following: bundled SQLite means one behaviour across every Android version, and it is what
let Mihon drop requery.

The work looks contained. Every query in `:anime:data` goes through `AndroidAnimeDatabaseHandler`,
the only place `executeAsList`/`executeAsOne` appear; repositories call `handler.awaitList { … }`
and would not change shape. It was left out of the dependency-injection work because it is a change
to the data layer and deserves its own verification.

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
| anime components in Mihon's `AndroidManifest.xml` | `:anime:services`' and `:anime:player`'s own manifests, merged in by the build |
| `Preference.MultiLineEditTextPreference`, `MPVConfPreference`, `EditTextInfoPreference` | **cannot be moved** — Mihon's `Preference` hierarchy is `sealed`, so no module of ours may extend it. See the player README |
| `StorageManager.getMPVConfigDirectory` and friends | `animato.anime.player.PlayerStorage`, resolving the base directory from `StoragePreferences` |
| `Tracker.animeService` | `eu.kanade.tachiyomi.data.track.animeService`, an extension that asks `this as? AnimeTracker` |
| `LocaleHelper.getSimpleLocaleDisplayName` | `animato.anime.player.getSimpleLocaleDisplayName` |
| `Padding.mediumSmall` | `animato.anime.player.mediumSmall` |
| `Preference.deleteAndGet` | `animato.anime.player.deleteAndGet` |
| `TachiyomiTheme.playerRippleConfiguration` | `animato.anime.player.playerRippleConfiguration` |
| `SourcePreferences.incognitoAnimeExtensions` | `aniyomi.domain.source.service.AnimeSourcePreferences` |
| `LibraryPreferences.hideHiddenCategoriesSettings` | `aniyomi.domain.library.service.AnimeLibraryPreferences` — Mihon's `Category` has no `hidden` field and its library never hides one |
| `TrackPreferences.showNextEpisodeAiringTime` / `trackOnAddingToLibrary` | `aniyomi.domain.track.service.AnimeTrackPreferences` |
| `AniChartApi` in `eu.kanade.tachiyomi.util` | `animato.anime.services.airing.AniChartApi`, taking tracker/track pairs instead of the details screen's item type |
| `relativeDateTimeText` in Mihon's `DateText.kt` | `animato.anime.ui.util` — it goes past "today" to hours and minutes, which only an air time needs |
| `CustomIcons.Magnet` | `animato.ui.icons.Magnet`, an extension on Mihon's icon set rather than a member of it |
| `CategoryListItem`'s hide button | `animato.anime.ui.category.AnimeCategoryListItem` |
| `TrackItemSelector(isManga)` | `animato.anime.ui.track.TrackEpisodeSelector` |
| `UpdatesDeleteConfirmationDialog(isManga)` | `animato.anime.ui.updates.EpisodesDeleteConfirmationDialog` |
| `SetIntervalDialog(isManga)` | `animato.anime.ui.entries.SetAnimeIntervalDialog`, on `AnimeFetchInterval`'s own ceiling |
| `StorageScreenContent(isManga)` and `CommonStorageScreenModel` | `animato.ui.storage`, written against a `StorageCategory` of id and name rather than either library's model |
| `ChangeCategoryDialog` / `LibraryTabs` taking `Category` | `animato.ui.category` / `animato.ui.library`, generic with an id and a label |
| `RemoveMangaDialog` taking a `Manga` | `animato.ui.browse.RemoveEntryDialog`, taking the title it was only ever reading |
| `PlayerSettingsGesturesScreen.SkipIntroLengthDialog` | `animato.anime.ui.entries.SkipIntroLengthDialog` — a per-anime setting shown from the anime page should not live in a settings screen object |
| `MainActivity.INTENT_SEARCH_TYPE` + `DeepLinkScreenType` | `animato.ui.deeplink.DeepLinkScreenType` — the deep-link activities live in library modules and cannot see the app module |
| `shouldExpandFAB` for `LazyGridState` | `animato.ui.components` — Mihon has the `LazyListState` one; the anime details list is a grid |
| `List<EpisodeList.Item>.applyFilters` | `animato.anime.ui.entries` — a services-layer helper taking a screen model inverts the layering |
| `AnimeSource.icon` | `eu.kanade.domain.source.anime.model` in `:anime:ui` |
| `HosterState` inside `QualitySheet.kt` | `animato.anime.player.HosterState` — a model, not a screen |
| `CustomButtonFetchState` inside a settings screen model | `animato.anime.player.CustomButtonFetchState` |

---

## Bugs inherited from Aniyomi, fixed rather than carried

Found while porting, all in code that is now ours. Each was wrong on its own terms, not a
difference of opinion with upstream.

| Where | What | Fix |
| --- | --- | --- |
| `AnimeLibraryUpdateNotifier` | the Download action passed `Notifications.ID_NEW_CHAPTERS`, the *manga* group, so tapping it dismissed the manga group and left the anime notification showing | uses `AnimeNotifications.ID_NEW_EPISODES`, agreeing with the two actions beside it |
| `ExtensionUpdateNotifier` | anime and manga extension updates shared `ID_UPDATES_TO_EXTS`, so an anime notification replaced a pending manga one and the user lost that list | anime updates post under `-403`, beside Mihon's `-401`/`-402` rather than on top |
| `NotificationReceiver` | two `openEpisodePendingActivity` overloads differing only in `Episode` vs `Int`, which is how a call site came to pass a notification id where an episode was meant | distinct names: `openEpisodePendingActivity` and `openAnimeEntryPendingActivity` |
