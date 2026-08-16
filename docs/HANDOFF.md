# Handoff — where the project stands

A snapshot taken 2026-08-16, the day the repository became public.

This file is deliberately short on things that are written down properly elsewhere, and long on the
things that are only true right now and would otherwise be re-derived expensively. When it disagrees
with `ARCHITECTURE.md` or `ROADMAP.md`, they win — they are maintained; this is a snapshot.

---

## Read these first, in this order

| File | What it settles |
| --- | --- |
| `ARCHITECTURE.md` | why Mihon is a dependency and not a fork, and the rules that follow from it |
| `ROADMAP.md` | what to build next and why, with every claim checked against the code |
| `UPSTREAM_DIVERGENCE.md` | debts carried over from the port, with their costs |
| `docs/BRANDING.md` | the palette, and which screens genuinely need replacing |
| `.github/RELEASING.md` | the signing keystore and the secrets |

## The four rules that cost the most to break

Taken from `ARCHITECTURE.md`, repeated here because a new reader meets them after they have already
been broken otherwise.

1. **Never edit a Mihon file.** Copy it into a module we own, or find a seam. The measured cost of
   the alternative is at the top of `ARCHITECTURE.md`: 776 conflicting files on the Aniyomi branch
   against 2 on this one.
2. **Never change `:anime:source-api` signatures.** Every anime extension in existence compiles
   against them.
3. **Never merge the anime and manga databases.** Mihon migrates its schema constantly; merging
   forfeits every future migration.
4. **The five exceptions** — files we own that sit at Mihon's paths — are listed in
   `ARCHITECTURE.md`. Adding a sixth is a decision, not a convenience.

`sync_mihon.yml` merges upstream every Monday. A conflict outside that table means the boundary was
crossed, and is a bug rather than a merge to resolve.

---

## State, in numbers

| | |
| --- | --- |
| Modules | 23 |
| Our Kotlin | ~77,500 lines — `anime/` 68,300, `animato-app` 5,800, `animato-ui-kit` 3,400 |
| Our test files | 16 |
| Migration phases done | 14 of 17 (`ARCHITECTURE.md` has the table) |
| Screens that are ours | Home, Library, Discover, Updates, Downloads, Search, Sources, Onboarding, Title page, Tracking hub |
| Alphas published | 9, `v0.1.0-alpha` through `v0.1.0-alpha.10` |
| Build time | ~16 minutes for a signed release |
| Shipped ABIs | `arm64-v8a`, `armeabi-v7a` — no universal APK, no x86 |

**Still owed:** phase 1 (identity and release pipeline) is in progress, phase 3
(`:animato:ui-kit` generalisation) is started, phase 7 (Aniyomi backup importer) has not begun.

### Against the two reference points

Neither number is a percentage of anything measurable; both are judgements, and the reasoning
matters more than the figure.

**Against Mihon — roughly at parity, plus a half.** Everything Mihon does, Animato does, because
Mihon *is* the base and is consumed unedited. On top of that sits a complete anime half: sources,
downloads, library updates, a player, seven trackers, and a unified library that shows both content
types through one lens. What Animato does not have is Mihon's maturity — its release history, its
extension ecosystem's trust, and the bug reports of a large installed base.

**Against Aniyomi — ahead on everything except installed base and time in the field.** Nine of the
findings in the pre-code research closed themselves the moment Mihon became the base: the two-release
lag, the stalled releases, the half-finished `.pb` store migration, hoster failover, two-way tracker
sync, custom themes. `ROADMAP.md` opens with that table. The gap that remains is that Aniyomi has
users and Animato has one.

**The honest weak point** is that most of this has been verified by reading code and running unit
tests, not by using the app. Everything in `ROADMAP.md` marked *unverified: test on device* is
genuinely unknown.

---

## What happened in the last session

### The UI session: every screen the app has, rewritten

The owner sent twelve screenshots and one sentence that framed all of it — *I never liked Mihon
because the interface does not feel good, and there is a lot of room to rethink the UX so someone
new can use it*. What followed was a design pass, then eleven commits that rebuilt the app's
surface. The load-bearing decisions, so they are not relitigated:

- **The lens is one global value with one control.** A `contentType` and a `libraryFilter` used to
  disagree, which is how a screen headed *Anime* came to show manga chapters. Now: one preference,
  one top-bar button, and the glyph *is* the state — a full circle for All, the same circle
  half-shaded when narrowed. A filtered app has to look filtered from across the room.
- **One control per question, and never two in the same shape.** Library's chip row mixed a medium,
  a state and a derived state. Now the lens asks which half, the chips ask which shelf, and a sheet
  asks what state a title is in — because a title can be several states at once and a chip row
  promises one.
- **Every destination merged both halves.** Home, Library, Discover, Updates, Downloads, Search,
  Sources & extensions and the title page were all per-half and are now one screen each. The title
  page was the last one, and the biggest: it owns the chrome and hands the deep tools — scanlator
  filters, seasons, notes, migration — back to the original screen through *All options*, because
  those are six thousand working lines that have nothing to do with how a page looks.
- **The one exception to the lens rule** is the type chip on *Sources & extensions*, which shows on
  every row regardless. That screen exists to tell anime and manga extensions apart, so the answer
  cannot be a thing that sometimes disappears.
- **Discover works with zero sources**, on AniList's public API. A fresh install used to open on an
  empty screen and a sentence explaining the emptiness.

Three things the design asks for are deliberately absent, each because the data is not there: the
download failure reason in words (neither half stores one), *See all* on Discover's rails (it would
link to a screen that does not exist), and the Categories card in the library quick sheet (the
picker is per-half and there is no shared one).

### The reader cannot be restyled, and that is a rule rather than a gap

Its chrome is wrapped in `TachiyomiTheme` by Mihon's own `setComposeContent`, inside Mihon's
`ReaderActivity`. Changing it means editing a Mihon file — the rule that keeps merges working — or
writing a reader, which means the viewer stack, zoom, page transitions and the webtoon path. The
same sweep found the **player** drawing itself in Mihon's colours too, and that one *is* our file
and was a one-line fix.

### The organisation that was made and then not used

`Animato-app/Animato` exists on GitHub and is not the project. It was created while working out how
to make releases publicly readable, and abandoned once the cost was clear.

It was made as a **new repository with the history pushed into it**, which is not the same as a
GitHub transfer, and the difference is the whole story:

- a transfer leaves a **301 redirect** behind; a push does not;
- a transfer **carries the releases**; a push carries commits, branches and tags and nothing else,
  because releases are GitHub metadata rather than git objects.

So the organisation copy had the code and none of the nine alphas, and every installed build — which
names `Mo3bdlaa/Animato` in its `BuildConfig` — would have been stranded with no redirect to follow.
Making this repository public instead cost one settings toggle, kept the releases, and let the builds
already on phones update themselves.

Leave the organisation repository alone rather than deleting it: it is harmless, and it is the reason
anyone finding it should not assume it is a mirror. If the project ever does move there, do it with
**Transfer**, which keeps both things a push threw away.

### The updater: what "HTTP error 404" meant

It was not a bug. GitHub deliberately answers **404** rather than 403 for an unauthenticated request
to a private repository, so as not to confirm that it exists. The updater was working; the repository
was private. Making it public is the whole fix, and it is done.

Worth knowing, because it took three attempts to find: the updater had failed silently twice before
for unrelated reasons — Mihon's `updaterEnabled` is `hasProperty("enable-updater")` and so was false,
and `:animato-app` had no serialization plugin, so `@Serializable` generated nothing and threw at
runtime inside the check's own `catch`. Both are fixed, and `Settings → Check for updates` now
reports the actual outcome rather than nothing. That row is the diagnostic; use it first.

### The repository name is no longer written down

It used to appear in a `buildConfigField` and in two workflow env vars, all saying
`Mo3bdlaa/Animato`. It now comes from `GITHUB_REPOSITORY`, which GitHub Actions sets for every run,
with `animato.releaseRepo` in `gradle.properties` as the fallback for local builds — which are never
distributed. The fork guards ask `github.event.repository.fork`, which is what they always meant.

This mattered more than it looks. `build_push.yml` compared `github.repository` against the written
name as a *step condition*, so after the move a release tag would have built, skipped signing and
publishing without a word, and reported success.

### Two facts about signing, established by experiment

- **`keytool` lowercases the alias** in both JKS and PKCS12. A keystore created with
  `-alias "Animato"` reports `animato`. The workflow compares case-insensitively anyway.
- **Leave the `ALIAS` secret empty** when the keystore holds one key. The workflow reads the real
  alias out of the keystore. That logic exists because the Windows-without-a-JDK path produces a
  GUID for an alias, which no secret could be expected to match.

### GitHub lists `alpha.10` before `alpha.7`

Cosmetic. That view sorts by tag as text, and `"1" < "7"`. Our own updater parses each tag into a
`SemanticVersion` and orders numerically, with a test asserting `alpha.10 > alpha.9`.

---

## Open immediately

1. **Confirm the updater on a device.** `Settings → Check for updates` on `alpha.10` should now say
   the build is up to date rather than reporting a 404. That row reports the real outcome, so it is
   the diagnostic to reach for first — and it is the only part of the updater that has never been
   seen working rather than merely tested.
2. **Put the new UI on a phone.** Eleven commits rebuilt every screen and none of them has been
   seen on a device. The RTL pass in particular is unverified: the screens were written with
   mirroring in mind and nobody has looked at one.
3. **Onboarding shipped and settled the policy question by refusing it.** Official portals are
   named and nothing else — no install control, no pre-filled repository, nothing bundled. Adding a
   repository sends you to *Sources & extensions*, which asks which half it serves; a paste field on
   the onboarding step could not know.

## Open, unblocked, in rough order of leverage

Numbers are the tracked task ids.

| | | Why now |
| --- | --- | --- |
| #22 | Casting over FCast | five years open upstream, and the hard part — `HttpServerService` rewriting every video, subtitle and audio URL — is already running on every external playback |
| #18 | Cross-device sync | the highest-voted unbuilt feature anywhere, and the backup half already writes both libraries into one file |
| #19 | Delete downloads no longer in the library | small, asked for often |
| #17 | Android TV | the highest-reacted issue in the donor |
| #15 | Extension update check | absent in both halves; `ExtensionApi` is `internal` to Mihon's `:app` |
| #24 | Verify the donor's player bugs | **needs a phone.** Cannot be closed from a repository |

And one that costs almost nothing: **long-press to speed up playback**. Among the most requested
upstream, absent here, and noticed by every user on their first episode.

## Deferred by decision, not blocked

**Trakt.** Investigated and parked — the owner's call, on the grounds that seven trackers already
ship and none of them is asking for an eighth. Written down so the next session does not re-derive
it:

- It would be an **anime-only tracker**: Trakt does shows and films, so it follows the Simkl shape
  — `AnimeOnlyTracker`, five files, roughly 500 lines.
- **No credentials to inherit.** Aniyomi has no Trakt (checked the donor branch) and neither does
  Mihon, so unlike Simkl — whose `client_id` and `client_secret` in `SimklApi.kt` are Aniyomi's,
  carried over with the port — this one needs an app registered at `trakt.tv/oauth/applications`.
- `AnimeTrackerIds.TRAKT = 103L` is free. The two ids there carry Aniyomi's numbers so imported
  backups keep their links; Trakt has no such constraint because no backup can contain one.
- Trakt supports the **device-code flow**, which needs no redirect URI and no manifest entry, and
  is the only OAuth shape that works on a television remote. That makes it the cleanest tracker
  here to sign into, not the worst.
- The real work is not the API. Trakt has **no anime concept**: it numbers episodes inside seasons
  while anime sources usually number straight through, so `Frieren episode 12` is not necessarily
  Trakt's `S1E12`. Either read `seasons` and map, or restrict to single-season shows and say so.
  Progress is also a watch history rather than a number, the same three-request rewrite `SimklApi`
  already documents.

**FlareSolverr.** Parked further out than Trakt, and for a better reason than priority: *the
problem it was wanted for is already solved.* ROADMAP §2 has the full analysis — the short version
is that Cloudflare is beaten on the phone, by a visible WebView earning a clearance cookie into
Android's process-wide `CookieManager`, which every extension, every tracker and both halves then
send. That shipped with #20.

What is left is an optional extra for the minority who already run a FlareSolverr at home, where a
second machine on a second IP does beat a phone. It writes to the same two public places the
on-device path writes to — `networkHelper.cookieJar` and `networkPreferences.defaultUserAgent` —
so it stays an addition rather than a rewrite whenever it is picked up. Nothing about it decays
while it waits.

## Things that cannot be settled without a device

Listed so they are not repeatedly re-investigated from the code, which has already been done:

- whether the PiP teardown leak survived the port — the `onDestroy` path is complete *if it runs*
- whether the playback path honours `aniSkipEnabled`
- crash on large video lists, frame tearing, landscape layout breakage
- the RTL pass on the screens we wrote ourselves
- **the torrent stack** — `libtorrserver` costs 19.9 MB per ABI, the single largest native library
  in the build, and has never once been switched on. Keep-or-drop should follow someone running it

---

## Conventions

- Commits are authored `Mo3bdlaa <3bdlaa.20@gmail.com>`.
- Development happens on `main`.
- The signing keystore is generated on a machine its owner controls, and never passes through
  anything else. `.jks` files and base64 encodings of them are excluded by `.gitignore`, and the
  history has been checked for both.
- No pull requests are opened against Mihon. Upstream is consumed, not contributed to.
