<div align="center">

<img src="./docs/branding/tv-banner.png" alt="Animato" width="560"/>

# Animato

### Your anime & manga universe, unified.

One app, one library, both kinds of story.

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue?labelColor=27303D)](/LICENSE)
![Status: pre-release](https://img.shields.io/badge/status-pre--release-4169A1?labelColor=27303D)

</div>

---

> **Pre-release.** There is no stable build yet, only alphas, and the database schema is not
> frozen. Animato does **not** upgrade in place over an existing Aniyomi install — bring your
> library across with a backup import. See
> [ARCHITECTURE.md](ARCHITECTURE.md#why-users-must-not-upgrade-in-place-from-aniyomi).

## Download

Alphas are published on the [Releases](https://github.com/Mo3bdlaa/Animato/releases) page, four files each. Once installed, the
app checks for the next one itself.

| You have | Take |
| --- | --- |
| A phone or tablet | `…-arm64-v8a.apk`, or `…-armeabi-v7a.apk` if that one will not install |
| A television or TV box | `…-armeabi-v7a-tv.apk`, or `…-arm64-v8a-tv.apk` if your set is 64-bit |

Start with `armeabi-v7a-tv` on a television even if the hardware is 64-bit: plenty of sets run a
32-bit system, and the 64-bit file then fails with nothing more than *App not installed*. Needs
Android 8.0 or newer.

The `-tv` builds are the same app without the manga reader — about 22–29 MB smaller — and with the
manga half hidden to match. Do not put one on a phone: it installs happily, under the same name,
and the manga is simply gone.

## What it is

A reader and a player in one place. Animato keeps anime and manga in a single library, with one
set of categories, one search, one download queue and one place to pick up where you left off —
instead of asking you to decide which app you are in before you have decided what you want to
watch or read.

Content comes from extensions you install and configure yourself. Animato ships none of it.

## What it does

- **One library.** Series and shows in the same grid, filtered and sorted together, or apart when
  you want them apart.
- **One-tap continuation.** The first screen is the next chapter and the next episode, whichever
  you touched last.
- **Search across sources.** Search once and see what every installed source has, without knowing
  in advance which one carries it.
- **Downloads that stay readable.** The queue groups by title and range rather than listing every
  chapter, so a hundred queued items still fits on a screen.
- **Tracking inside the title.** AniList, MyAnimeList, Kitsu, Shikimori and Bangumi for both
  halves; Simkl and Jellyfin for anime. Where you are already looking, not on another screen.
- **A reader and a player built for the content.** Chrome that disappears when it is not wanted;
  gestures, playback speed, subtitles and external-player handoff on the anime side.
- **Counts where you decide.** Every cover in the library and on Home says how many items are
  downloaded and how many are unwatched, and Home has a *Your downloads* row for what plays with no
  signal.
- **Select, then act.** Tap an episode's number to select it, select a run of them, and download or
  mark the lot at once. Pull down on any title to refresh it.
- **Playback that survives a bad connection.** The buffer is sized to the device's memory rather
  than fixed, and a dropped stream reconnects instead of ending the episode.
- **A way back from a broken build.** If the app crashes twice on launch, it opens a recovery
  screen that checks for a newer release first — so a crash fixed upstream is a download, not a
  reinstall.
- **Backups that other apps can read.** One file holds both libraries, written in Aniyomi's format —
  so Aniyomi can open it in full and Mihon can open the manga in it. Aniyomi and Mihon backups import
  the same way, and a restore names anything whose extension is missing before it starts.

Not all of that is wired up yet — this is a pre-release, and
[ARCHITECTURE.md](ARCHITECTURE.md) tracks what is built and what is not.

## Android TV

Animato installs on a television, appears on its home screen with its own banner, and is driven
entirely with the remote. Every row and button shows a ring when the remote reaches it.

In the player, **up** or **down** brings up the controls with play/pause already selected, and the
controls stay up while you move between buttons. **OK** pauses when nothing is selected, and
**left** and **right** seek while the controls are hidden.

Take a `-tv` file from [Download](#download).

## Sources

Animato takes content from two different kinds of place, and the difference is worth knowing.

**Extensions** are small Android packages, one per site, installed from a repository. Sources &
extensions holds the repositories and the list. This is the model Mihon and Aniyomi use, and it is
where the manga comes from.

**Stremio addons** are the other shape: a web address that answers JSON. Nothing is installed and
nothing runs inside the app, so an addon cannot crash it or read its storage — the app only ever
talks to it. Sources → **Extension stores** → **Stremio** is where they are added: the screen
suggests four worth starting with, lists around five hundred the community has published, and takes any
other addon's `manifest.json` link the same way. The **Stremio** segment beside *Installed* and
*Available* is what is already added — open one to browse it, or remove it.

That long list is two lists merged. Stremio publishes its own collection as JSON and the app reads
it at launch, which keeps the popular half current. The rest is a snapshot of
[stremio-addons.net](https://stremio-addons.net), which has no API, scraped by
`docs/stremio/build-addon-directory.py` and shipped with the app — so it is as fresh as the release
you are running. A workflow refreshes it monthly, and asks every address whether it is still there:
an addon that answers *404* or *410* is dropped, and one that merely fails to answer is kept, since
that says more about the machine asking than about the addon. Addons that describe themselves as adult are marked in that snapshot and hidden
unless *Show NSFW sources* is on.

The store groups them by what they actually do, read off each manifest rather than off its
description — **Browse and play** works on its own, **Video only** adds playback behind catalogues
you already have, **Catalogue only** shows posters and plays nothing until a video addon joins it,
and **Subtitles** never appears as a source at all. Of the addons listed, roughly two thirds serve
video and a fifth are catalogues alone.

Live television has two shapes and its own doors. **Extension stores → IPTV** and the **IPTV**
segment in Sources hold both.

An **M3U playlist** is the common one: paste the address of a `.m3u` file and its channels become a
source. Where a provider demands a particular `User-Agent` or `Referer` — the usual reason a
playlist answers 403 to everything — the playlist says so, and all three conventions for saying it
are read: VLC's `#EXTVLCOPT`, the `#EXTHTTP` JSON object, and the `|Key=Value` suffix Kodi appends
to the address. Nothing is installed and nothing is stored but the address — the file is read on demand and
kept for as long as the app is open, so restarting is how you get today's list. Channels are grouped
by the playlist's own `group-title`, searchable by name or group, and the file's order is kept
because it is editorial and there is nothing better to sort by.

A **Stremio addon** can also carry channels, and that works the same way. The store's live-TV door is a filter rather
than a second mechanism — an IPTV addon is a Stremio addon whose declared type is `tv` — and an
addon that publishes films and channels appears under both headings, which is true of it.

A channel arrives as an ordinary entry whose single row says **Live**. It keeps no progress and is
never marked as seen, because there is no such thing as being part-way through a channel, and it is
fetched once rather than re-asked on every library update.

Addons split the job between them and meet on a shared id, so a working setup is usually more than
one:

| | Provides |
| --- | --- |
| **Anime Kitsu** | An anime catalogue |
| **Cinemeta** | Films and series, with posters and descriptions |
| **Torrentio** | Video |
| **OpenSubtitles v3** | Subtitles, for anything with an IMDb id |

A catalogue addon has no video and a stream addon has no idea what anything is called; installing
one of each is what makes a title playable. Addons that only supply streams or subtitles never
appear as sources — they work behind the ones that do.

### Configuring Torrentio

Torrentio's plain address works, but its useful form is configured first, and the configuration
travels **inside the address** rather than in a settings screen. So it is set up on its own page and
pasted in afterwards:

1. Open <https://torrentio.strem.fun/configure> in a browser.
2. Pick your providers. For anime, add **Nyaa.si**, **AniDex** and **TokyoTosho**.
3. Sort by quality, and filter out `CAM`, `SCR` and `480p` unless you want them.
4. Copy the install link rather than pressing Install — Install tries to hand the address to the
   Stremio app, which is not what you are using it for. The link looks like
   `https://torrentio.strem.fun/providers=…|sort=…/manifest.json`, and the settings you chose are
   that middle segment.
5. Paste it into Sources → Stremio addons.

Torrentio serves torrents, so playback goes through the bundled torrent server. It is on by default
and shows a one-time notice before the first torrent explaining that peer-to-peer sharing uploads as
well as downloads; it can be turned off under Settings → Player → Torrent, and it shuts down when
the player closes.

## Writing an extension

[docs/extension-template](docs/extension-template) is a working skeleton for a new source — the
build, the manifest, the class, and the workflow that publishes it as a repository you can add in
the app. Start with its [GUIDE.md](docs/extension-template/GUIDE.md), which walks the whole thing
through, including doing it from a phone.

## Design

<img src="./docs/branding/logo-on-dark.png" alt="The Animato mark" width="200"/>

The mark is the blue dragon. Its sources are kept full size in [docs/branding](docs/branding), and
`build-icon.py`, `build-splash.py` and `build-tv-banner.py` there regenerate the launcher icon, the
themed icon, the launch screen and the TV banner from them.

Palette, typography, component rules and a screen-by-screen specification live in
[docs/BRANDING.md](docs/BRANDING.md).

## Building

```
./gradlew :animato-app:assembleRelease
```

Requires the Android SDK with API 37 and NDK `29.0.14206865`. Minimum supported device is
Android 8.0 (API 26).

Add `-Panimato-tv` for the television build, which leaves out the manga reader's native libraries
and fixes the app to anime.

## Documentation

| | |
| --- | --- |
| [ARCHITECTURE.md](ARCHITECTURE.md) | how the app is put together, and what is built so far |
| [ROADMAP.md](ROADMAP.md) | what is worth building next, and why — with the evidence |
| [docs/BRANDING.md](docs/BRANDING.md) | brand and interface specification |
| [UPSTREAM_DIVERGENCE.md](UPSTREAM_DIVERGENCE.md) | where Animato differs from the code it builds on |
| [docs/APK_SIZE.md](docs/APK_SIZE.md) | what the APK is made of, and why it is the size it is |
| [docs/extension-template](docs/extension-template) | how to write and publish an extension |

## Credit

Animato is built on the work of others, and depends on that work continuing:

- **[Mihon](https://github.com/mihonapp/mihon)** — the manga app Animato is built on. Apache-2.0.
- **[Aniyomi](https://github.com/aniyomiorg/aniyomi)** — the origin of the anime half. Apache-2.0.
- **[Tachiyomi](https://github.com/tachiyomiorg)** — where both began.

Animato is an independent project. It is not affiliated with, endorsed by, or supported by the
Mihon or Aniyomi teams, and problems with it should not be reported to them.

## Disclaimer

Animato hosts zero content. It reads and plays what the user's own configured sources provide, and
the developers have no affiliation with those sources.

## License

<pre>
Copyright © 2015 Javier Tomás
Copyright © 2024 Mihon Open Source Project
Copyright © 2025 Animato Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
