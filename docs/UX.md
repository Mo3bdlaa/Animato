# Animato UX — the whole apartment, room by room

Every screen below is designed as the answer to one question: **what does the person standing here
want next?** Implementation cost and file ownership are deliberately ignored in this document —
`ARCHITECTURE.md` governs *how* things get built; this file governs *what should exist*. Where it
disagrees with `docs/BRANDING.md` §7, this file wins. The brand sheet remains the visual authority
for palette, type and the manga DNA.

---

## The six rules

1. **Never empty.** Discover renders from public metadata before a single source is installed.
   Every empty state carries a verb — a button that moves the person forward. An empty screen with
   only a kaomoji on it is a dead end wearing a smile.
2. **One lens, everywhere.** All · Anime · Manga is a single global state shown as the same chips
   in the same place on every content screen. Flip it on Home and Library arrives already flipped.
   Nothing else filters content silently.
3. **Continue is the product.** The most common session is "play the next episode / read the next
   chapter". That must be one tap from launch, and it opens the content itself — not a title page.
4. **Media-neutral seams.** Where the two halves meet, the words are neutral: *Updated*, *Opened*,
   *In progress*, *Done*. Media words — Chapter, Episode, Read, Watch — appear only once you are
   inside one half.
5. **Manga DNA in moments, not chrome.** Halftone, speed lines and ink belong to the splash,
   onboarding, empty states and celebrations. Toolbars, lists and settings stay quiet. An app that
   shouts everywhere emphasises nothing.
6. **No concept before its moment.** A newcomer never needs the word "extension" to read their
   first chapter. Architecture vocabulary appears only inside the screen that manages it.

---

## Navigation

Five tabs, labelled with single words that cannot truncate in any locale:

| Tab | Owns |
| --- | --- |
| **Home** | Continue, library stats, latest updates |
| **Library** | Everything saved, in categories |
| **Discover** | Trending, seasonal, source browsing, search into the world |
| **Updates** | The unified new-content feed |
| **Downloads** | Queue, storage, cleanup |

Today's "Download ..." truncation is a defect against this table — the label is **Downloads**.

**The lens.** Three states — `All · Anime · Manga` — one global preference, carried by a single
**top-bar icon** on Home, Library, Discover, Updates and Search: a full outlined circle for All,
the same circle **half-shaded and blue** when filtered to one medium — the icon itself says you are
looking at part of the collection, with no text label. Tapping it opens a three-item menu (check on
the current state, captioned *Applies everywhere*); choosing All restores the full circle. The
two-state toggle on today's Home and the hidden third state in today's Library filter both go away.
When the lens is Anime or Manga, type badges vanish from covers (they would be redundant); under
All, a small type chip sits on each cover corner — the one place mixing needs disambiguation.

**Search.** The magnifier appears on every tab and opens the *same* screen: query field, lens
chips, results in two groups — **In your library** first, then **From your sources**. This is the
"how do I even add an anime" fix: search is one tap from anywhere and looks everywhere at once.

**Settings** stays behind the gear on Home, and in the overflow elsewhere.

---

## First run

Six screens, under a minute, every one skippable. The flow ends in a living app, not an empty one.

1. **Brand moment.** Ink black, the wordmark, one line — *Read. Watch. Track.* — one button.
2. **What do you follow?** Anime / Manga / Both. Seeds the lens.
3. **Content languages.** Multi-select; seeds source suggestions and search. Arabic listed
   prominently, not alphabetically buried.
4. **Sources, honestly.** One sentence: *Animato ships empty by design — you choose where content
   comes from.* Below it: suggestions of official portals **by name only** (per the legal
   position: nothing bundled, nothing pre-filled), and a paste field — *Have a repository URL?*
   Skippable.
5. **Bring your history.** Three cards: *Import an Aniyomi backup* (the importer already built) ·
   *Sign in to a tracker* (AniList / MAL — their lists seed Library and Continue immediately) ·
   *Start fresh.*
6. **Done** → lands on **Discover**, which is already alive via metadata rails even with zero
   sources installed.

---

## Splash

Today's screen places the framed dark logo on plain black, so the logo's own panel reads as a grey
slab floating in the void. Replace it:

- Launch window: **Ink Black** (`#08080C`), both themes' `splash` colour tokens.
- Centered: the **transparent-keyed wordmark** — blue ANIMATO, white アニマト, speed-line accents —
  no frame, no panel.
- Light theme: Paper background, dark wordmark.
- ≤ 400 ms, fade out. No spinner: if startup runs long, Home shows skeleton cards instead of the
  splash lingering.

---

## Home

Top bar: wordmark left, magnifier, lens button, gear.

1. **Continue** — horizontal rail. Card: 2:3 cover, a 3 dp blue progress bar flush along the bottom
   edge, one-line title, subtitle `Ch. 254 · 2h ago` / `Ep. 12 · yesterday`. A `NEW` pill when
   something newer than your position exists. **Tap opens the reader/player at your position** —
   not the title page. Long-press opens the quick sheet (below).
2. **Latest updates** — up to five rows (thumbnail, title, `Chapter 1187 · NEW`), then *See all* →
   Updates tab. Tap opens the content.

No stat chips: Home is the two things a session actually needs — what you were in the middle of,
and what arrived since. The counts live in Library, where the numbers are the content.

Empty state (no library at all): one card — *Your shelf is empty. Find something worth keeping* —
with a **Discover** button.

Folded-in defect: the Continue rail must respect the lens. Today `HomeScreenModel` merges both
histories and nothing filters by the `contentType` it already carries — which is exactly how a
Manga row appears under an Anime toggle.

---

## Library

Top bar: title, magnifier, lens button, **one** filter icon, overflow. Category chips (horizontal,
only when more than one category exists — never a dropdown).

Grid of covers. Unread count badge top-right. Type chip only under the All lens. Covers stay clean
— progress lives on Continue cards, not here.

**One sheet** behind the filter icon, three sections:

- **Sort** — *Last updated · Last opened · Date added · A–Z · Unread count.* Neutral words: no
  "chapter", no "read" in sort labels, because the shelf holds both media.
- **Filter** — *Unread · Downloaded · Started · Tracked.*
- **Display** — columns (2/3/4), badges on/off, continue-button overlay on/off.

**Long-press a cover** → quick sheet: *Continue · Mark done up to here · Download next 5 ·
Categories · Remove.* Removing prompts about downloaded files (the cleanup setting's moment).

Empty states are lens-aware and honest about the cause:
- Lens Anime, no anime in library: *No anime on your shelf yet* → **Find anime** (Discover, lens
  kept).
- Lens Anime, **no anime sources installed**: *You don't have any anime sources yet* → **Add
  sources**. This names the exact confusion a new person hits today, instead of showing them an
  unexplained empty grid.

---

## Discover

The proof that the app is never empty.

Top bar: title, magnifier, lens button.

1. **Trending now** — rail fed by public tracker metadata (AniList's open GraphQL; Jikan as
   fallback). Renders with zero sources installed.
2. **This season** (anime lens) / **Popular now** (manga lens) — same feeds.
3. **Top rated** — same feeds.
4. **Your sources** — pinned sources as cards; tap browses that source (its latest / popular).
5. **Manage** — one row into *Sources & extensions*.

Tapping a metadata title opens the title page in **preview mode**: full info, tracking add, and a
primary button — **Find in your sources** — which runs the global search for that title. Found in a
source → the page completes into a normal title page. Found nowhere → *No source carries this yet*
→ **Add sources**. Browsing the world comes first; wiring it to a source happens exactly when the
person asks to read.

Offline: last-fetched rails from cache, with a quiet banner.

---

## Title page — manga and anime

Header: blurred cover backdrop; cover; title; author; source chip; status. Action row:

- **Primary, resume-aware**: `Resume · Ch. 184` / `Watch · Ep. 1` — big, blue, one per page.
- Secondary: **In library** (heart — never the word "favorite"), **Tracking** (ring that shows
  sync state at a glance), WebView, Share.

Tabs: **About** (description, genre chips, links) · **Chapters / Episodes** · **Tracking** ·
**Also on** (the same title found in other installed sources — attach or migrate).

Chapter list: number-first rows, date, download glyph, read rows dimmed. A sticky range toolbar —
*Download next 5 / 10 / all · Mark read up to here.* Lists over ~100 items get a **fast scrubber**;
an 871-chapter title is a real case in the first user's library, and reaching chapter 400 must not
be a swiping marathon.

Episode list adds the thumbnail and summary toggles that already exist as preferences, seen
dimming, and per-episode download state.

---

## Reader and player

Both inherited designs are competent; the changes are trims, not rebuilds.

- **Reader**: brand progress bar; the chapter-transition page names what's next — *Next: Ch. 185 ·
  NEW*; settings sheet unchanged.
- **Player**: the gesture layer as shipped (double-tap seek, hold-to-speed-up, vertical
  brightness/volume). Add one top-bar affordance: **Play on…** — external player (VLC and friends,
  the existing setting made visible) and, later, cast targets. Remembered quality is already in.

---

## Updates

Lens button in the top bar. Feed grouped by day (*Today · Yesterday · date*). Row: thumbnail, title, item name
(`Episode 12` / `Chapter 291`), `NEW` pill, download glyph. **Tap opens the content directly.**
Swipe right — mark done; swipe left — download. Top bar: refresh, calendar (a later, optional
upcoming-episodes view fed by the same metadata as Discover).

Empty: *Updates land here when your library moves. Last checked 4:31* → **Refresh**.

---

## Downloads

Header line: *1.2 GB in downloads* · **Clean up** — runs the orphan sweep that already exists and
reports what it freed.

Sections: **Active** (progress, pause), **Queued** (drag to reorder, swipe to remove), **Failed**
(retry). Global pause/resume in the top bar. Footer: *See downloaded items* → Library with the
Downloaded filter on.

Empty: *Nothing downloading. Long-press any chapter list to queue a batch.*

---

## Sources & extensions — one screen, was two

Segments: **Installed · Available**. The lens applies here too — this is the direct answer to
"there is no way to tell anime and manga extensions apart": the list obeys the lens, **and** every
card carries a type chip regardless.

- **Installed**: icon, name, language, type chip, pin star, gear (source settings), and an
  **Update** pill when the extension update check (already rebuilt) has found one.
- **Available**: grouped by language, NSFW marked plainly, inline install. Fed by whatever
  repositories the person added.
- Top row: **Repositories** — the paste field again, plus the same official-portal suggestions
  from onboarding.

Trust prompts inline, in plain words.

---

## Tracking hub

Reachable from Settings and from any title page's Tracking tab. Account rows — AniList, MAL,
Kitsu, Shikimori, Bangumi, Simkl, Jellyfin — signed-in state, *Sync now*, last-sync time. Below: a
recent sync-activity feed. Per-title binding stays on the title page, where the title is.

---

## Settings — the new order

Ten entries, organised by activity, symmetric across the halves. No top-level entry is named after
a medium: "Anime" as a bucket dies, and the media live as **Reading** and **Watching** — siblings.

| New entry | Contains | Where it lives today |
| --- | --- | --- |
| **Appearance** | theme, app language, formats | Appearance |
| **Library** | categories, auto-update, both halves' library options | Library + half of "Anime" |
| **Reading** | the reader | Reader |
| **Watching** | player, gestures, decoders, subtitles, torrents, external player | buried inside "Anime" |
| **Sources** | browse options, repositories, NSFW, **Unblock a source** | Browse + a stray top-level row |
| **Downloads & storage** | download rules, auto-delete, orphan cleanup, storage usage | Downloads + half of Data & storage |
| **Tracking** | trackers and sync policy | Tracking |
| **Backup & data** | backup, restore, schedule, Aniyomi import | the other half of Data & storage |
| **Privacy & security** | app lock, secure screen, incognito | Security and privacy |
| **Advanced · About** | logs, battery, network · version, check for updates, licenses | Advanced, About, loose rows |

Every entry's subtitle says what is inside it. Settings search spans all of it.

---

## Components

- **Cover card**: 12 dp radius; unread badge top-right (blue pill, white count); type chip
  top-left under the All lens only; on Continue cards, the 3 dp progress bar flush at the bottom.
- **Chips**: lens and category chips are the same component; blue fill = active, outline = not.
- **Blue** (`#4169A1`) means *active or progress* — never decoration.
- **Empty states**: three reusable brand illustrations — halftone burst, speed-line sweep, ink
  splash — one sentence, one action button. The kaomoji faces retire. No empty screen without a
  verb.

## Copy

Seam-words are neutral: *Continue, In library, Updated, Opened, In progress, Done.* "Favorite"
never appears — the heart is *In library*. Arabic is a first-class locale: every new string lands
in `ar` in the same commit, and the RTL pass is part of each screen's definition of done, not a
follow-up.

---

## What this answers

| Complaint | Answered by |
| --- | --- |
| Splash is ugly | Splash spec — unframed keyed wordmark, no grey slab |
| Anime/Manga filter broken, filters "in the UI" | One global lens; the Home Continue defect named and folded in |
| Extensions indistinguishable | Sources & extensions — lens + type chip on every card |
| Doesn't match the brand sheet | Palette, type and DNA applied per screen; departures listed below |
| No trending | Discover metadata rails, alive with zero sources |
| Settings are a mess | Ten-entry activity-first order; Reading and Watching as siblings |
| Can't control the library | One filter/sort/display sheet; category chips; long-press quick actions |
| Can't search to add an anime | One-tap search everywhere; library + sources merged results; lens-aware empty states that say *you have no anime sources* out loud |
| Newcomers get lost | First-run flow; never-empty rule; no concept before its moment |

## Brand sheet: adopted and departed

**Adopted:** the five-tab bar; Continue cards with on-card progress; the Latest-updates list; Discover's rail structure; title-page tabs; the downloads layout; source
type segmentation; Paper light mode.

**Departed, with reasons:**
- The mock Home shows a **two-state** Anime|Manga toggle → this plan uses the **three-state
  global lens**. A unified app needs *All* to be a first-class state, or mixed content looks like
  a bug (it does today).
- The mock Library carries a **status chip row** (Reading, Watching, Completed…) → folded into the
  single filter sheet. Two chip rows compete for one strip of screen, and those statuses belong to
  trackers, not the shelf.
- The mock Sources screen lists **named third-party repositories** → suggestions stay
  official-portals-by-name only, nothing bundled or pre-filled, per the legal position taken for
  onboarding.

**Round-one design decisions** (from the first Claude Design pass, confirmed): the lens moved from
a chip row to the top-bar icon with the half-shaded filtered state; Home's stat chips were removed
outright; the NEW pill stays Accent. All three documents were amended to match.
