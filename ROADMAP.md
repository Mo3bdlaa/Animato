# Roadmap

What Animato is for, past the port. Two sources feed this list and they disagree about order, so both
are kept:

- **the research done before any code was written** — what actually kills this app in practice, from
  Aniyomi's issue tracker, its troubleshooting docs, and what happened to the source ecosystem;
- **reaction counts on the two upstream issue trackers** — what the most people asked for.

The first is better at "why is this deleted on day one". The second is better at "what would the most
people cheer for". A plan built from only the second loses the first, which is how the existential
tier came to be missing from a later plan and why this file exists.

**Every state below is checked against the code, not remembered.** That matters more than it sounds:
this research was written about *Aniyomi*, and Animato is no longer Aniyomi. Several of its findings
were answered by the move to a Mihon base without anyone doing the work, and one of its predictions
turned out to be already built.

---

## What the research got right, and what changed underneath it

| Its finding | Now |
| --- | --- |
| "Behind Mihon by two full releases" (#2370) | **Gone by construction.** Mihon *is* the base; there is nothing to catch up to |
| "No new stable release since 2025-10-28" | **Gone by construction.** We release, and since alpha.6 the app updates itself |
| "The `.pb` store format migration is half-done and breaking extensions" (#2371, #2372) | **Done, both halves** — protobuf, gzip, legacy auto-migration. Arrived free with the Mihon base |
| "Automatic hoster failover" (#2156) | **Done** — `HosterLoader.getBestVideo`, ported with the player |
| "Trakt, MangaBaka and Hikka are missing" | MangaBaka and Hikka **ship with Mihon** for manga. Trakt is still missing everywhere |
| "No two-way tracker sync — it pushes, never pulls" | **Done.** It was one guard: `if (service !is EnhancedAnimeTracker) return` |
| "Custom themes, open since 2021" (#288) | Mihon ships **15**. Animato's palette sits above them |
| "Anime and manga cannot be separated" (#2331) | **Answered by design** — one library with a content lens, `ALL → MANGA → ANIME`. Nobody is told to install a different app |
| "The unified library is the one thing no competitor has" | Still true, and it is ours |

That is nine findings closed without a line of feature work, because the architecture was the fix.

---

## Tier 1 — existential

The app ships with no content by design. If content is not flowing in the first five minutes,
nothing else matters.

| | | State |
| --- | --- | --- |
| 1 | Automatic hoster failover (#2156) | ✅ **done** |
| 2 | **Cloudflare** (#1909) | 🟡 **the on-device half is done**, see below |
| 3 | **Onboarding that ends with a working store** | ❌ not started |
| 4 | The `.pb` store format (#2371, #2372) | ✅ **done** |

### 2 — Cloudflare, and the seam that was there all along

The most common bug class in the donor by a distance, and no longer only about sources: it takes
trackers down too. FlareSolverr is among the most-reacted requests upstream.

**An earlier version of this entry said it was blocked and needed a decision about editing a Mihon
source file. That was wrong**, and worth leaving the correction visible rather than quietly
rewriting, because the mistake has a shape worth remembering: the question was framed as *"where do
we add an interceptor?"* — and FlareSolverr does not need one.

`NetworkHelper` does assemble its interceptors in a `private val` on a final Mihon class with no
override point, and every extension draws from that client, and most rebuild on top of it with
`newBuilder()`. All true. All irrelevant, because what FlareSolverr returns is **a cookie and a
user-agent**, and both of those already live in shared, writable state:

```kotlin
class AndroidCookieJar : CookieJar {
    private val manager = CookieManager.getInstance()   // Android's global cookie store
}
```

- The cookie jar is not a private jar. It is Android's process-wide `CookieManager`, and
  `NetworkHelper.cookieJar` is a **public val** whose `saveFromResponse` is a public interface
  method. Every client derived with `newBuilder()` inherits it, so a cookie written once is sent by
  every extension.
- The user-agent is `NetworkPreferences.defaultUserAgent`, a **public `Preference<String>`**, read
  through `defaultUserAgentProvider()` on *every request* rather than captured at build time. So
  setting it takes effect immediately, everywhere. That matters because Cloudflare binds a
  clearance cookie to the user-agent that earned it — a cookie without a matching agent is useless.

So the whole integration is ours, in our own files, touching nothing of Mihon's:

1. a preference holding the FlareSolverr address;
2. `POST /v1` with `{"cmd": "request.get", "url": …}`;
3. write `solution.cookies` through `networkHelper.cookieJar.saveFromResponse(url, …)`;
4. set `networkPreferences.defaultUserAgent` to `solution.userAgent`.

One caveat to keep honest: `UserAgentInterceptor` only fills the header in when a request does not
already carry one, so an extension that sets its own user-agent keeps it and its clearance cookie
will not match. That is a per-extension limit, not a reason the approach fails.

### And what it actually took: nothing new

Chasing FlareSolverr turned out to be chasing the wrong thing twice over. It cannot be a library —
it is Python driving a *desktop* Chrome, and its value is not its code but its being a different
machine on a different IP, which is precisely what an app on the phone cannot be. And the free ways
to host it are the worst ones for the job: Cloudflare distrusts datacenter addresses, so a free VPS
is challenged harder than a phone on a home connection.

Which leaves the phone — where the two things that decide this are already in our favour. A
residential IP, and a person holding it who can tap a box.

And the pieces to use them were all present:

- Mihon's `CloudflareInterceptor` tries the challenge in a WebView **that is never attached to a
  window**. It can pass a challenge that solves itself, and it can never pass one that wants a tap.
  Thirty seconds, then failure, forever.
- `WebViewScreen` shows a real, visible WebView — and sets its user agent to
  `headers["user-agent"] ?: defaultUserAgentProvider()`, the very agent OkHttp will send. Cookies a
  WebView earns go into Android's process-wide `CookieManager`, which is what `AndroidCookieJar`
  reads.

So a check passed there is a check passed for every extension, every tracker, and both halves — with
nothing copied and nothing configured. **The mechanism already worked. Nobody was ever pointed at
it.** The repair was a bridge, not an engine:

- `CloudflareBlock` recognises the failure. It has to match on the message: Mihon's
  `CloudflareBypassException` is `private`, so what escapes is a bare `IOException` of the same type
  a timeout produces. Tested against timeouts, dead hosts, wrapped chains and a self-referential
  cause.
- The anime browse error says *Cloudflare is blocking this source* and offers **Pass the check**
  first, with a shield. The same WebView action was already there — third, labelled "Open in
  WebView", under whatever text the exception happened to carry.
- **Settings → Unblock a source** lists every online source, both halves, and opens any of them.
  That is the part that reaches manga, whose screens are Mihon's and not ours to change.

*Still open: FlareSolverr as an optional extra for anyone who does run one at home, where it beats
the phone. It writes to the same two places, so it is an addition rather than a rewrite.*

### 3 — Onboarding

Today's first run, in full: install, open, find an empty app, search Reddit or YouTube, find an
unofficial guide, copy a raw URL, paste it into a settings screen nobody mentioned. Mihon's
onboarding is Theme → Storage → Permissions → Guides, and not one step ends with a source installed.

The largest drop-off in the funnel and among the cheapest to fix — and a policy question before a
coding one: which store to offer, and whether to offer one at all.

---

## Casting — the blocker is gone, and the mechanism is already running

The research called this the most important technical finding in it, and it was right, but it is now
better than it described.

The stated reason casting sat open for five years is that extensions hand out URLs carrying expiring
tokens and required headers that no external player or receiver can replay. The research spotted that
a local HTTP server had recently landed for an unrelated purpose, and that this was exactly the
missing piece — a local proxy that re-injects the headers.

In Animato that is not a possibility to connect up. **It is built, wired and used on every external
playback today:**

```kotlin
val (success, port) = startHttpServerService(context, resolvedSourceId)
val servedVideo = video?.copyHttpServer(port)      // http://localhost:$port
```

`Video.copyHttpServer` rewrites the video URL *and every subtitle and audio track* to point at the
local server. `HttpServerService` is a foreground service with its own notification channel, and
`PlayerLauncher` waits for it to be listening before handing anything over.

So casting is no longer "solve the header problem". It is "point a receiver at a URL that already
works", and the remaining questions are about receivers, not about video:

- Google's Cast SDK pulls in Play Services, which is at odds with a FOSS build. **FCast is the way
  in** — open, no Play Services, and it takes a plain URL.
- The server binds `localhost`. Serving a TV on the same network means binding the LAN interface,
  which is a real change and a real security question.

**This moves up.** It was scored as medium-and-risky when the risk was the video pipeline. The video
pipeline is done.

---

## Tier 2 — most asked for, for years

| | Demand | State | Size |
| --- | --- | --- | --- |
| **Cross-device sync** | 69 upstream; highest-voted unbuilt feature in the donor | ❌ no sync package | large |
| **Android TV** | #162, the highest-reacted issue in the donor, open since 2021 | ❌ no leanback, no TV launcher | medium |
| **Casting** | #78, third most requested, open 5 years | ❌ — but see above | **medium, and the hard part is done** |
| **F-Droid** | 34 + 17 | ❌ `isFossBuildType` exists, unused | packaging, not code |
| **Light novels** | 28 | ❌ | a different app, honestly |
| **Delete downloads no longer in the library** | 16 | ❌ | small |
| **A second source for the same title** | 15 | ❌ | large |
| **Import your MAL list** | 10 votes | ❌ | medium — real barrier for anyone arriving with a library |
| **Edit an entry's details** | #237, top-ten since 2021 | partial | medium |
| **Volume keys across the app** | 24 | ✅ already works, reader and player | — |

**Sync** is where Animato is unusually well placed: the backup creator already writes both halves
into one file, so sync is that plus a remote and a merge by last-modified. Most of the hard part is
built — the same shape as the casting finding.

**The unified tracker** (#1403) is worth stealing: one dialog that pushes to every signed-in service
and colours the failures red. With seven trackers wired, the cost is a screen, and it answers the
real complaint — *which service failed silently?*

---

## The player

Already there: AniSkip, picture-in-picture, subtitle styling, gesture/audio/decoder settings, custom
Lua buttons.

**Reported bugs, and what can honestly be said about them from here.** These were filed against
Aniyomi. The code came across; whether the bug did cannot be settled without a device, and guessing
either way would be dishonest. They need a session with a phone, not a session with a repository:

| Reported | What the code says |
| --- | --- |
| Player leaks on PiP close; audio continues, crash on app switching (#2124, #2363, #2179) | `onDestroy` stops the HTTP server, abandons audio focus, releases the media session, unregisters the noisy receiver, removes both MPV observers and calls `player.destroy()`. The teardown is complete **if it runs** — the donor's bug is that dismissing PiP may not reach it. **Unverified: test on device** |
| AniSkip skips despite being off (#1049) | `aniSkipEnabled` exists and is read by the settings screen. Whether the playback path honours it is **unverified: test on device** |
| Quality, subtitle and audio reset every episode (#1737) | No preference persists a per-anime track choice. **Real, and ours to fix** |
| Crash on large video lists (#2047), frame tearing (#2288) | **Unverified** |

**Wanted, and absent:**

- ~~**Long-press to speed up**~~ — **done**, and the entry it replaces was wrong in a way worth
  leaving visible. It said *"nothing implements it; the only `longPress` in the player is the
  custom-button Lua hook"*. There was a long-press on the video surface all along — bound to the
  screenshot sheet — and around it sat the remains of a speed boost: a `DoubleSpeed` update whose UI
  was a commented-out line, and a release handler restoring a speed nothing raised, read once inside
  a `pointerInput(Unit)` that never restarts and therefore frozen at whatever the speed was when the
  player opened. Live, that would have snapped playback back to the wrong speed on release.

  Holding is now a **choice** — speed up (default), screenshot, or nothing — with the speed itself a
  setting, because 2× suits dialogue and is far too fast for a fight. Changing the default was only
  honest once the screenshot had somewhere else to live: that gesture was the **only** route to it,
  so the more sheet now opens it too.
- **OpenSubtitles** (#441, #1762, #142, since 2022) — confirmed absent. Its value is that it works
  *regardless of extension quality*, so it fixes missing subtitles at the root rather than per source.
- True black for OLED (#2271), one-handed reachability (#2062).

---

## Downloads

Incomplete downloads that never finish (#1326, open since 2024), downloaded content not recognised
(#1917), SD-card problems. And **resume is missing from both halves** — checked, not assumed; the
`resume` hits in both downloaders are coroutine continuations, not HTTP ranges. The research said
Mihon had it and Aniyomi did not; on this base neither does.

---

## Arabic — the position, corrected

The research called Arabic and Spanish an empty market slot and recommended leading with it. That
still holds as a *position*. What it got wrong is the starting point, and the correction makes it
cheaper rather than harder:

| | Reality |
| --- | --- |
| Arabic UI strings | **901 of 1088 already translated (83%)** on the manga side |
| Arabic anime strings | **473 of 636 (74%)** |
| RTL | `android:supportsRtl="true"` is declared |

So this is not a translation project. What is actually missing is the last fifth of the strings, a
real RTL pass on a device with the layouts we built ourselves, and — the part that decides it —
**Arabic sources that work out of the box**, which lands squarely on the onboarding item above.

The research noted that i18n is untested and that the donor's player crashed on a broken translated
string. That failure mode is real, cheap to guard, and untested here too.

---

## Tier 3 — noticed, unclaimed

- Recommendations from AniList and MAL; a "recently added" tab; Trakt.
- Sub-categories in the library.
- Search inside the episode list, bulk selection, more sort modes, untruncated episode names.
- Layout breakage in landscape (#2351, #2353, #2290) — filed against the donor's UI; ours is Mihon's
  plus ported anime screens, so **unverified**.

---

## Torrent — the strategic note

Of the competitors, AnymeX and Awery retreated to tracker-only to survive legally; Hayase went to
torrent streaming. Torrent does not die to a Cloudflare block or a DMCA notice the way a scraped site
does.

That code is already here: `libtorrserver`, `TorrentServerService`, `TorrentServerApi`,
`TorrentServerUtils`, and it costs 19.9 MB per ABI — the single largest native library in the APK,
larger than mpv and FFmpeg together. It has never been switched on and tested. **That is the most
expensive untested thing in the build**, and the decision to keep or drop it should be made after
someone runs it, not before.

---

## Carried over from the port

Debts with a known cost, tracked in UPSTREAM_DIVERGENCE.md.

- **No extension update check at all**, either half. `ExtensionApi` is `internal` to `:app` and was
  only ever called from inside Mihon's `CheckForUpdates`; Mihon has no periodic job for it any more.
- **158 preference accessors** are still functions where Mihon's are properties.
- Mihon's `#3761`/`#3762` and `#3772` are not mirrored onto the anime side.

---

## The five with the most leverage

1. **Onboarding that installs a working store** — fixes the first run, and it is what makes the
   Arabic position real rather than aspirational.
2. **Cloudflare** — the largest source of failures, and no longer blocked on anything.
3. **Casting over FCast** — five years open, and the part everyone assumed was hard is already
   running in this repository.
4. **Cross-device sync** — the highest-voted unbuilt feature anywhere, and the backup half of it is
   built.
5. **Android TV** — the highest-reacted issue in the donor, and an anime app on a television is the
   obvious place to want one.

**Long-press to speed up playback** used to sit here as the cheap one worth doing anyway. It is
done — see the player section, including what the entry describing it got wrong.
