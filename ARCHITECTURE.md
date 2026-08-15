# Animato architecture

Animato is an anime and manga app built **on top of** [Mihon](https://github.com/mihonapp/mihon),
not forked from it.

This branch starts from Mihon's own history. That is not bookkeeping — it is the update mechanism.
Git computes conflicts from the merge base, so a branch descended from Mihon today conflicts with
tomorrow's Mihon only in the files we changed.

Measured, on the same repository, merging the same upstream:

| Branch | Descends from | Conflicting files |
| --- | --- | --- |
| `claude/aniyomi-revival-upgrade-n0c0zp` | Aniyomi (forked Jan 2024) | **776** |
| this one | Mihon `77e88a21` | **2** |

## The rule

> **Never open a file that belongs to Mihon.**

Not to fix a bug, not to add a parameter, not for one line. Every change we would have made inside
Mihon's code is re-expressed as one of:

1. a **new file we own** — copy the component, adapt it, keep theirs untouched
2. an **extension point Mihon already offers** — dependency injection, public composables, interfaces
3. a **wrapper** around Mihon's public API

Aniyomi took the fourth option — editing in place — and produced 13,862 lines of edits spread
across 248 upstream files. It then went two years without an upstream sync, because it could not.

## Layers

```
:animato:app     assembly — Application, MainActivity, home, settings structure, theme, branding
:animato:ui-kit  our generalised components (ItemCover, EntryToolbar, …) — adapted copies
:anime:*         anime: source-api, domain, data, player, ui
──────────────── nothing above edits anything below ────────────────
mihon (:app as a library, :domain, :data, :core, …)   consumed, never edited
```

Mihon's `:app` is consumed as an Android **library**. Its `App` and `MainActivity` reach our APK
through manifest merging; our application module needs no source of its own to boot it.

## Theming instead of rewriting

Mihon's screens carry **4** hard-coded colours in 186 presentation files; everything else reads
`MaterialTheme`. Wrapping the app in our own theme restyles all of Mihon's screens with no edits.

So we rewrite a Mihon screen **only when unification demands it** — the library, which must show
both content types. Manga download settings do not need rewriting to look like ours; they already
will.

Every Mihon screen we replace is a permanent maintenance cost, because it grows the surface below.

What the theme has to express, and which screens genuinely need replacing, is specified in
[docs/BRANDING.md](docs/BRANDING.md) — palette, components, and the nine screens read off the
brand sheet. The navigation section there is the one part that is not restyling: Animato's tab
bar is Home / Library / Discover / Updates / Downloads against Mihon's Library / Updates / History
/ Browse / More, so **Home and Downloads are new destinations we own** and settings leave the tab
bar for the overflow menu.

## The metric

> **Number of distinct Mihon symbols our code references.**

After this design an upstream update produces no merge conflicts at all — it produces compile
errors in our files, bounded by this number. It was **339** in the Aniyomi codebase. Track it; make
it fall.

Porting `:anime:domain` onto Mihon 0.20.4 broke in six places, which is what that number buys: a
short, specific list instead of a merge. Mihon had renamed `getObject` to `getObjectFromString`,
turned its preferences from functions into properties, deleted `CategoryUpdate`, and moved
`GetLibraryManga`. Each was a compile error naming a file and a line.

Two of the six were not upstream changes at all but boundary violations inherited from Aniyomi:
`EntryCover` and `Category.hidden` were fields Aniyomi had added *inside* Mihon's files. They are
now `animato.domain.entries.EntryCover` and `animato.domain.category.AnimeCategory` — ours, in our
packages. Anime categories were always a separate table with a separate id space; sharing Mihon's
model was the thing that made the field necessary in the first place.

## Files we own that live at Mihon's paths

Every entry here is a file we must reconcile by hand when Mihon changes it. Adding to this list is
a deliberate decision, not a convenience. Keep it under ten.

| File | Why |
| --- | --- |
| `app/build.gradle.kts` | application → library; app id and version become build config fields |
| `settings.gradle.kts` | registers our modules |
| `.github/workflows/*` | Mihon's target their repository, releases and website |
| `.gitignore` | Mihon's does not exclude keystores; a leaked signing key is unrecoverable |
| `README.md` | the repository's front page cannot be another project's |

## Branding without editing anything

Everything visible that identifies the app is done by **overriding a resource name**, not by
editing a file. An application module wins resource merging over its library dependencies, so
defining a name in `animato-app` replaces Mihon's definition of it while Mihon's own file stays
byte-identical — and Mihon's manifest, which still points at `@mipmap/ic_launcher`, resolves to
ours without a `tools:replace`.

| Name we redefine | Mihon's | Effect |
| --- | --- | --- |
| `string/app_name` | "Mihon" | the app is called Animato |
| `mipmap/ic_launcher` | their adaptive icon | our launcher icon, monochrome layer included |
| `color/splash` | `@color/accent_blue` | the launch window is ink black |
| `drawable/ic_mihon_splash` | their splash mark | our logo on the launch screen |

An override replaces **one configuration** at a time. `color/splash` has a `night` value upstream,
so overriding the default alone left dark mode on Mihon's grey; both had to be redefined. Check
`aapt2 dump resources` on the built APK rather than assuming — it lists every configuration of a
name and shows which one won.

These four are the cheapest kind of divergence we have: they cost nothing at merge time, because
the files they override are still untouched.

`sync_mihon.yml` merges upstream into a branch and opens a pull request every Monday. A
conflict outside the table above is a signal that the boundary has been crossed, not a merge
to resolve.

## Working rules

- **Never edit Mihon code.** Add a seam or copy the file into a module we own.
- **Never change `:anime:source-api` signatures.** Every anime extension compiles against them.
- **Never merge the anime and manga databases.** Mihon migrates its schema constantly; merging
  forfeits every future migration.
- **Prefer duplication over coupling** across the line. A copy that drifts is cheaper than a shared
  abstraction that breaks on every upstream release.
- **Move code, don't rewrite it** — except in the UI, where Aniyomi's generalisations must be
  re-homed in `:animato:ui-kit` rather than pushed back into Mihon's files.

## Migration state

| Phase | | State |
| --- | --- | --- |
| 0 | Mihon as a library, booting under our application id | done |
| 1 | Animato identity, CI, release pipeline | in progress |
| 2a | `:anime:source-api` — 26 files, the frozen extension contract | done |
| 2b | `:anime:domain` — 104 files | done |
| 2c | `:anime:data` — the anime database and its repositories | done |
| 2d | `:anime:source-local` — local anime files | done |
| 3 | `:animato:ui-kit` — generalised components re-homed | started |
| 3a | components needing player preferences move with the player instead | phase 4 |
| 4 | `:anime:services` — extensions, downloads, library update, torrent | started, see its README |
| 5 | `:anime:player` | |
| 6 | `:anime:ui` and a home screen combining both tab sets | |
| 7 | Importer for Aniyomi backups | |

### Why the player is not next

The plan had the player before the UI, on the assumption that it was self-contained. Measuring its
imports says otherwise: it reaches the anime download manager, the anime extension manager, the
library update job and the torrent service — 27 files and 6,400 lines that sit above `:anime:data`
and below both the player and the screens.

So the player is near the top of the dependency chain, not the bottom, and the layer underneath it
has to exist first. That layer is `:anime:services`.

What the player needs from Mihon, by contrast, is nearly all still there: `NetworkHelper` and
`SecurityPreferences` only moved into `core/common`, and the notification, work-manager and
clipboard helpers are unchanged. The obstacle is our own unported code, not upstream drift.

The old branch stays green and buildable throughout. It is the donor, not the product.

## Why users must not upgrade in place from Aniyomi

Mihon squashed its migration history: its chain is 13 migrations, Aniyomi's is 32, and they differ
from migration 1. An Aniyomi database reports a schema version Mihon's chain cannot service.

Animato has never published a release, so no installed base is affected. The first release must be
this base. Users coming from Aniyomi arrive through a backup import (phase 6), never by upgrading
over their existing install.
