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
| 5c | player settings screens | **blocked — see below** |

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

## What 5c is blocked on

Aniyomi's player settings screens — 22 files, about 3,300 lines — plug into Mihon's settings
framework, and to do so Aniyomi added three item types to it: `MultiLineEditTextPreference`,
`MPVConfPreference` and `EditTextInfoPreference`.

**Mihon's `Preference` and `PreferenceItem` are `sealed`.** A sealed hierarchy can only be extended
from the module that declares it, so no amount of care lets us add those three from here. This is
not a boundary we chose to respect; it is one the compiler enforces.

There are two ways through, and both belong to phase 6 rather than here:

1. **Render the player's settings ourselves.** `ARCHITECTURE.md` already puts the settings structure
   in `:animato:app`, and the brand specification replaces the settings tab with an overflow entry —
   so phase 6 is rebuilding this surface regardless. Writing our own preference rendering now would
   mean writing it twice.
2. **Ask upstream.** Mihon may well accept the hierarchy being opened up; that is a conversation, not
   a workaround.

The screens are not in this module. They are a `cp -r` from the donor branch at
`app/src/main/java/eu/kanade/presentation/more/settings/screen/player`, and the analysis above is
what to read before doing it.

Until then the player runs on its preference defaults: `PlayerPreferences`, `AudioPreferences`,
`SubtitlePreferences`, `DecoderPreferences`, `GesturePreferences` and `AdvancedPlayerPreferences` are
all bound and read, there is simply no screen to change them from.
