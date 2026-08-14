# Animato architecture

Animato continues [Aniyomi](https://github.com/aniyomiorg/aniyomi), which continues
[Mihon](https://github.com/mihonapp/mihon). Mihon is manga only; the anime half is Aniyomi's
addition and has no upstream of its own.

That asymmetry decides everything below.

## The problem this structure exists to solve

Aniyomi added anime by editing Mihon's files in place. Every screen was opened and an anime tab was
threaded through it. Measured against Mihon at the point this was written, a merge produced **777
conflicting files** — and of the 354 conflicts inside `app/`, only **9** were anime files. The other
345 were shared screens that had been modified from the inside.

So the cost is not that anime exists. The cost is *how* it was attached. Anime built alongside
Mihon rather than inside it would conflict in roughly 9 files instead of 354.

## Three layers

```
animato  ──depends on──▶  anime  ──depends on──▶  platform
```

The arrow never points backwards. `platform` must not know that `anime` exists.

### platform — Mihon, untouched

Mihon's code, kept byte-identical to upstream. **Do not open a file here.** Not to fix a bug, not
to add a parameter, not to sneak in one line. The entire value of this layer is that
`git merge mihon/main` applies cleanly to it, and that value is lost the first time it is
compromised.

A change that seems to require editing platform code is a signal that a seam is missing. Add the
seam (see below) rather than the edit.

### anime — ours outright

Everything anime, in its own modules:

| Module | Holds |
| --- | --- |
| `:anime:source-api` | The extension contract. **Frozen** — see below. |
| `:anime:domain` | `Anime`, `Episode`, `Season`, interactors |
| `:anime:data` | The anime database (already separate from manga's) |
| `:anime:player` | mpv, hosters, subtitles, AniSkip, PiP, torrent |
| `:anime:ui` | Anime library, browse, history, tracking screens |

This layer has no upstream, so it is free to be reshaped whenever that serves us. That freedom is
the whole reason the anime/manga split is drawn where it is.

**One exception: `:anime:source-api` is frozen.** Every anime extension is compiled against it.
Changing it breaks all of them, and the extension ecosystem is already fragile — the official
repository is archived and many sources have died. Treat it like a published ABI. New capability
goes in a new interface that old extensions can ignore, never in a changed signature.

### animato — the app

Our identity and assembly: navigation, theming, icons, branding, and the unified surfaces that span
both content types (`LibraryEntry`, the unified library). Also the seams.

## The seams

The only places that touch platform code. Each should be an insertion of a line or two, never a
rewrite:

1. **Startup** — register the anime dependency-injection module
2. **Navigation** — contribute anime tabs
3. **Settings** — contribute the anime settings section
4. **Backup** — include anime data
5. **Deep links** — route anime URLs

If the seam count grows much past this, the layering is being eroded and should be corrected rather
than accommodated.

## What this does and does not buy

It buys a **small blast radius**. When Mihon changes something we depend on, the build breaks in a
handful of known places, loudly, at compile time — instead of silently, everywhere.

It does not buy automatic adaptation. Kotlin is statically typed; a changed signature upstream is a
compile error here no matter how the code is arranged. A compile error is the good outcome. The
outcome worth designing against is behaviour quietly drifting out from under us.

## Working rules

- **Never edit platform code.** Add a seam instead.
- **Never change `:anime:source-api` signatures.** Extensions depend on them.
- **Never merge the anime and manga databases.** Mihon migrates its schema constantly (32
  migrations and counting); merging them forfeits every future migration and forces a rewrite of
  upstream-owned data code. Add a table of our own when something needs to span both.
- **Move code, don't rewrite it.** Migration steps should be mechanical and behaviour-preserving,
  verifiable by CI at every step.
- **Prefer duplication over coupling** across the anime/platform line. A copy that drifts is
  cheaper than a shared abstraction that breaks on every upstream release.
