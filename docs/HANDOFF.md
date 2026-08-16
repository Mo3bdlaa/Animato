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
| Our test files | 14 |
| Migration phases done | 14 of 17 (`ARCHITECTURE.md` has the table) |
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
2. **`#21`, onboarding, is blocked on a policy answer rather than on code**: which extension store
   to offer, and whether to offer one at all. `ROADMAP.md` explains why it is the highest-leverage
   item — today's first run ends in an empty app and a search for an unofficial guide. Worth knowing
   before deciding: `:anime:source-api` is unchanged from Aniyomi's, so the extension repositories
   that already exist work here without anything being hosted.

## Open, unblocked, in rough order of leverage

Numbers are the tracked task ids.

| | | Why now |
| --- | --- | --- |
| #22 | Casting over FCast | five years open upstream, and the hard part — `HttpServerService` rewriting every video, subtitle and audio URL — is already running on every external playback |
| #18 | Cross-device sync | the highest-voted unbuilt feature anywhere, and the backup half already writes both libraries into one file |
| #19 | Delete downloads no longer in the library | small, asked for often |
| #17 | Android TV | the highest-reacted issue in the donor |
| #15 | Extension update check | absent in both halves; `ExtensionApi` is `internal` to Mihon's `:app` |
| #25 | FlareSolverr as an optional extra | writes to the same cookie jar and user-agent preference the on-device path uses, so it is an addition rather than a rewrite |
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
