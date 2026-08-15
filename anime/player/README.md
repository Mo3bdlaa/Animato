# `:anime:player`

The video player: mpv playback, the on-screen controls, external-player hand-off, picture-in-picture
and the loaders that turn an episode into a stream.

73 files. It compiles, `:animato-app` depends on it, and `PlayerActivity` and the mpv, FFmpeg and
torrent-server native libraries are in the shipped APK.

## Stages

| | | State |
| --- | --- | --- |
| 5a | loaders and the video resolver | done |
| 5b | playback core and controls | done |
| 5c | player settings screens | done |

## The seam

`animato.anime.player.PlayerEpisodeVideoResolver` implements the `EpisodeVideoResolver` that
`:anime:services` declares. Aniyomi had the downloader import `EpisodeLoader` and `HosterLoader`
from `ui.player.loader` directly, so a background service depended on the player; now the downloader
depends on an interface and the player supplies it. That interface was the last unbound type in the
graph — with it bound, every type the anime modules resolve has a binding.

## Things that were declared in the wrong place

Each of these was a model, a constant or an extension that Aniyomi had put inside a Mihon file or a
Compose screen. They are files of their own here, and the full list is in `UPSTREAM_DIVERGENCE.md`.

| What | Was | Now |
| --- | --- | --- |
| `HosterState` | inside `QualitySheet.kt` | `animato.anime.player.HosterState` |
| `CustomButtonFetchState` | inside a settings screen model | `animato.anime.player.CustomButtonFetchState` |
| `playerRippleConfiguration` | inside Mihon's `TachiyomiTheme.kt` | `animato.anime.player.PlayerRipple` |
| mpv config, fonts, scripts, shaders directories | methods on Mihon's `StorageManager` | `animato.anime.player.PlayerStorage` |
| `mediumSmall`, `deleteAndGet` | inside Mihon's `Padding` and `Preference` | `animato.anime.player.PlayerExtensions` |
| `getSimpleLocaleDisplayName` | inside Mihon's `LocaleHelper` | `animato.anime.player.PlayerLocale` |

The pattern is worth naming: the loaders imported a Compose sheet to get at a *sealed class*, and
the view model imported a *settings screen* to describe its own loading state. Neither is about
drawing. Moving them is what let the loaders ship in 5a without dragging the whole UI along.

## The blocker that was not one

Aniyomi's player settings screens plug into Mihon's settings framework, and to do so Aniyomi added
three item types to it: `MultiLineEditTextPreference`, `MPVConfPreference` and
`EditTextInfoPreference`.

Mihon's `Preference` and `PreferenceItem` are `sealed`, so those three cannot be added from here.
This module's notes concluded from that that 5c was blocked until phase 6 built a preference
renderer of our own. **That was wrong**, and it cost the screens a phase.

The hierarchy has an escape hatch: `CustomPreference` takes a `@Composable` and renders exactly it.
Nothing needed extending. The three item types are three functions in `animato.ui.settings` that
return a `CustomPreference`, so a settings screen declares them the way it declares any other row,
and upstream keeps one preference hierarchy while we keep none.

The lesson is narrower than "read the file": the sealed-ness was real and the conclusion drawn from
it was not checked against what the sealed type already offered.

## Where the settings live

| | |
| --- | --- |
| `animato.ui.settings` | the two generic rows — multi-line text, and text with a caption and a rule |
| `animato.anime.player.settings.mpvConfPreference` | the third, which also writes an mpv config file |
| `animato.anime.player.settings` | the eight settings screens, the custom-button editor and the config editor |

Two things Aniyomi read off Mihon that were not there to read: `BasePreferences.deviceHasPip()`,
which is a device capability rather than a preference and is answered by the package manager, and
`InfoPreference(enabled = …)`, which does not exist — Mihon hides a disabled preference rather than
greying it out, so leaving the item out of the list says the same thing.
