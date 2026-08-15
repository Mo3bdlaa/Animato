# `:anime:ui`

The anime screens: details and episode list, sources and extensions, library, updates, history,
migration and tracking.

**In progress, and deliberately not registered in `settings.gradle.kts` yet.** The sources are here
so the work is not lost, but nothing builds them until they compile. Adding the `include(":anime:ui")`
line is the last step, not the first.

## Scope

Measured on `aniyomi-donor`, every UI path containing "anime": **113 files, 22,064 lines.**

| Area | Files | Lines | |
| --- | ---: | ---: | --- |
| entries — details, episodes, tracking | 17 | 7,818 | copied; everything else navigates into it |
| browse — sources, extensions, migration | 47 | 6,591 | |
| library | 10 | 2,077 | |
| updates | 4 | 1,081 | |
| history | 6 | 885 | |
| category, download, stats, storage, deeplink | | balance | |

The areas are mutually referential — `AnimeScreen` pushes `BrowseAnimeSourceScreen`,
`MigrateAnimeSearchScreen` and `AnimeLibraryTab` — so they cannot be landed one at a time. Entries
was copied first because it is what the others open, not because it can compile alone.

## The work is not a copy: Mihon has moved off Voyager's ScreenModel

This is the largest single item, and it is not visible until you compile.

| | Mihon 0.20.4 | Aniyomi donor |
| --- | --- | --- |
| File | `MangaViewModel.kt` | `AnimeScreenModel.kt` |
| Base class | `mihon.core.viewmodel.StateViewModel<S> : ViewModel` | Voyager `StateScreenModel<S>` |
| Coroutine scope | `viewModelScope` | `screenModelScope` |
| Obtained by | `viewModel()` | `rememberScreenModel { }` |

`voyager-core` is not on the compile classpath at all — Mihon's catalogue bundle carries only
`voyager-navigator`, `voyager-tab-navigator` and `voyager-transitions`, because Mihon no longer uses
Voyager's screen models. That is 70 unresolved `screenModelScope` references plus every class
declaration and call site.

There are two ways through and only one is right:

1. **Convert each screen model to `StateViewModel`.** More work, and it is what building *on* Mihon
   means — one view-model idiom, no extra dependency, and Voyager's screen models are on their way
   out upstream.
2. Add `voyager-screenmodel` to the catalogue and keep Aniyomi's. Cheaper today, and it leaves two
   idioms in one app permanently.

Take the first. The conversion is mechanical: `StateScreenModel<S>` → `StateViewModel<S>`,
`screenModelScope` → `viewModelScope`, `rememberScreenModel { X() }` → `viewModel { X() }`.

## Components that moved to `:animato:ui-kit`

Aniyomi generalised these by editing Mihon's own files. They are ours now, so the ported screens
need their imports rewritten rather than the components re-added.

| Aniyomi import | Here |
| --- | --- |
| `eu.kanade.presentation.entries.components.ItemCover` | `animato.ui.entries.ItemCover` |
| `eu.kanade.presentation.entries.components.ItemHeader` | `animato.ui.entries.ItemHeader` |
| `eu.kanade.presentation.entries.components.EntryToolbar` | `animato.ui.entries.EntryToolbar` |
| `eu.kanade.presentation.entries.components.MissingItemCountListItem` | `animato.ui.entries.MissingItemCountListItem` |
| `eu.kanade.presentation.entries.DownloadAction` | `animato.ui.entries.DownloadAction` |
| `eu.kanade.presentation.entries.EditCoverAction` | `animato.ui.entries.EditCoverAction` |
| `eu.kanade.presentation.entries.EntryScreenItem` | `animato.ui.entries.EntryScreenItem` |
| `HosterState` (was inside `QualitySheet.kt`) | `animato.anime.player.HosterState` |

Three are still unaccounted for and need finding or writing: `EntryBottomActionMenu`,
`DeleteItemsDialog`, `ItemDownloadIndicator`. `DotSeparatorText` and `SetIntervalDialog` exist in
Mihon under `eu.kanade.presentation.manga.components` and can be consumed there.

## What blocks completion

`eu/kanade/presentation/more/settings/screen/browse/components/anime` and the anime source
preference screens plug into Mihon's settings framework, whose `Preference` hierarchy is `sealed`.
That is the same wall as phase 5c, and it lifts in 6d when the settings structure becomes ours.
Those files are the ones to port last.
