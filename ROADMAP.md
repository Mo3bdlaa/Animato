# Roadmap

What Animato is for, past the port. Two sources feed this list and they disagree about order, so both
are kept:

- **the research we did before writing any code** — what actually kills this app in practice, from
  Aniyomi's issue tracker, its troubleshooting docs, and what happened to the source ecosystem;
- **reaction counts on the two upstream issue trackers** — what the most people asked for.

The first is better at "why does someone delete this app on day one". The second is better at "what
would the most people cheer for". A plan built from only the second one loses the first, which is
how the existential tier came to be missing from a later plan and why this file exists.

Every state below is checked against the code, not remembered.

---

## Tier 1 — existential

Without these the rest is arranging furniture in a house with no doors. The app ships with no
content by design; if a user cannot get content flowing in the first five minutes, nothing else
matters.

| | | State |
| --- | --- | --- |
| 1 | **Automatic hoster failover** when a video host fails | ✅ **done** — `HosterLoader.getBestVideo`, ported with the player |
| 2 | **Cloudflare — FlareSolverr integration** | ❌ **blocked, needs a decision.** See below |
| 3 | **Onboarding that ends with a working extension store** | ❌ |
| 4 | **The new `.pb` extension store format** | ✅ **done** — protobuf, gzip and legacy auto-migration, both halves |

### 2 — Cloudflare, and why it is not simply "write an interceptor"

The most common bug class in the donor by a distance, and it stopped being only about sources: it
takes trackers down too. FlareSolverr is among the most-reacted requests upstream.

The integration itself is small — a preference holding a FlareSolverr address, and an interceptor
that on a challenge posts the URL there and takes back the cookies and user-agent. The problem is
where to put it.

`NetworkHelper` builds one `OkHttpClient` and hands it to everything. Its interceptor list is
assembled inside a `private val clientBuilder` on a **final class in a Mihon file**, with no
constructor parameter and no override point. Every extension ultimately draws from that client, and
most extensions build their own on top of it with `network.client.newBuilder()` — so adding an
interceptor anywhere downstream, including `AnimeHttpSource.client`, is bypassed by exactly the
extensions that need it most.

Three ways out, in preference order:

1. **Upstream a seam to Mihon** — an injectable builder, or a list of extra interceptors. Small,
   general, and Mihon gets Cloudflare relief out of it too. Slow, because it depends on them.
2. **A third documented exception, editing `NetworkHelper.kt`.** There are two already, both in
   `app/build.gradle.kts` and both forced by Mihon being a library here. This would be the first in
   a Mihon *source* file, and that is a line worth naming out loud before crossing.
3. **Ours only** — trackers and our own requests, not extensions. Fixes the "AniList blocked by
   Cloudflare" case and leaves the main one.

*This is a decision, not a task.*

### 3 — Onboarding

Today's first run, in full: install, open, find an empty app, search Reddit or YouTube, find an
unofficial guide, copy a raw URL, paste it into a settings screen nobody mentioned. Mihon's
onboarding is Theme → Storage → Permissions → Guides, and not one of those steps ends with a source
installed.

This is the largest drop-off in the whole funnel and the cheapest to fix, and it is a policy
question before it is a coding one: which store to offer, and whether to offer one at all.

---

## Tier 2 — most asked for, for years

Reaction counts are from the upstream trackers, open issues.

| | Demand | State | Size |
| --- | --- | --- | --- |
| **Cross-device sync** | 69 — the highest anywhere, near double the next | ❌ no sync package exists | large |
| **F-Droid** | 34 + 17 | ❌ `isFossBuildType` exists and is unused | packaging, not code |
| **Android TV** | 30 — the highest open one on Aniyomi | ❌ no leanback feature, no TV launcher | medium |
| **Light novels** | 28 | ❌ | a different app, honestly |
| **Casting** | 20 | ❌ not one line of cast code | medium, and risky |
| **Delete downloads no longer in the library** | 16 | ❌ | small |
| **A second source for the same title** | 15 | ❌ | large |
| **Two-way tracker sync** | 14 + 13 | ✅ **done** | — |
| **Edit an entry's details** | 14 | partial | medium |
| **Volume keys across the app** | 24 | ✅ already works, reader and player | — |

**Sync** is where Animato is unusually well placed: the backup creator already writes both halves
into one file, so sync is that plus a remote and a merge by last-modified. Most of the hard part is
built.

**Casting** deserves its caveat in public: Google's Cast SDK pulls in Play Services, which is at
odds with a FOSS build, and mpv does not cast. The cheap eighty percent is handing the stream URL to
an app that does cast — close to the external-player handoff that already exists.

---

## Tier 3 — noticed, unclaimed

- **OpenSubtitles**, recommendations, Trakt.
- **Sub-categories** in the library.
- **Arabic and Spanish are underserved** in this whole category. Not a feature — a position.

---

## Carried over from the port

These are not user requests; they are debts with a known cost, tracked in UPSTREAM_DIVERGENCE.md.

- **No extension update check at all**, either half. `ExtensionApi` is `internal` to `:app` and was
  only ever called from inside Mihon's `CheckForUpdates`; Mihon has no periodic job for it any more.
- **158 preference accessors** are still functions where Mihon's are properties.
- Mihon's `#3761`/`#3762` and `#3772` are not mirrored onto the anime side.
