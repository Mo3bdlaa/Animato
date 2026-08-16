# Animato — design brief and style guide

This file is a **handoff package for a design tool or a designer**. It is self-contained: everything
needed to produce high-fidelity screens is in here, without reading the rest of the repository. The
product reasoning behind these choices lives in `docs/UX.md`; the brand authority is
`docs/branding/brand-sheet.png`. Where the three disagree, UX.md decides behaviour and this file
decides pixels.

---

## The product, in one paragraph

Animato is one Android app for anime **and** manga: one library, one home, one update feed. The
core interaction is a single global **lens** — `All · Anime · Manga` — that filters every content
screen at once. The most common session is "continue where I left off", one tap from launch. The
app ships with no content sources by design; Discover stays alive anyway, fed by public metadata.
Tagline: *Your anime & manga universe, unified. Read. Watch. Track.*

## Hard constraints

- **Android phone, portrait.** Reference frame **393 × 852 dp**. Design for touch: targets ≥ 48 dp.
- **Dark theme first** (this is the default and the identity). Light "Paper" variant second.
- **Material 3 foundations** — bottom navigation bar, bottom sheets, switches — with the brand look
  layered on top. No web patterns: no hover states, no cursors, no horizontal page scroll.
- **RTL is first-class.** Arabic ships as a full locale. Deliver at least Home mirrored RTL with
  Arabic labels (provided below).
- Both media are equals. Never let a screen read as "a manga app with anime bolted on".

---

## Tokens

### Colour

| Token | Hex | Use |
| --- | --- | --- |
| Animato Blue | `#4169A1` | Primary actions, active states, progress. **Never decoration** |
| Ink Black | `#08080C` | Dark background |
| Surface | `#151516` | Cards, sheets, bars on dark |
| Paper | `#F2EEE5` | Light background |
| Muted | `#9A9690` | Secondary text, inactive icons |
| White | `#FFFFFF` | Primary text on dark |
| Success | `#22C55E` | Done / synced / downloaded |
| Warning | `#F59E0B` | Attention, pending |
| Error | `#EF4444` | Failures |
| Info | `#8B5CF6` | Informational accents |
| Accent | `#06B6D4` | Rare highlights (e.g. NEW pill) — use sparingly |

Rule of thumb: a dark screen is ink, surface, white, muted — **plus blue only where something is
active or in progress**. If a screen has blue in more than three places, something is wrong.

### Typography

- **UI font:** Noto Sans — Regular, Medium, SemiBold, Bold.
- **Japanese accents:** Noto Sans JP (brand moments only: splash, about, onboarding).
- Scale (dp/weight): screen title **24 Bold** · section header **18 SemiBold** · card title
  **15 Medium** · body **15 Regular** · secondary/caption **13 Regular, Muted** · chip label
  **14 Medium** · tab label **12 Medium**.
- Numbers (chapter counts, sizes, percentages) use the same font, tabular where aligned.

### Spacing and shape

- 4 dp grid. Screen edge padding **16**. Gap between rail cards **12**. Section spacing **24**.
- Cover cards: **2:3 ratio, 12 dp radius**. Chips: full-round. Sheets: 28 dp top radius.
- Progress bars: **3 dp**, blue on `#FFFFFF14` track, square ends, flush to the card's bottom edge.

### Iconography

Outlined, 24 dp, consistent stroke. Active bottom-nav tab = **filled icon + blue**, inactive =
outlined + muted. No colourful or duotone icons anywhere.

### Manga DNA — where it is allowed

Speed lines, halftone dots, ink texture, bold panel angles: **splash, onboarding, empty states,
celebrations only.** Toolbars, lists, grids and settings stay completely quiet. This restraint is
the brand: an app that shouts everywhere emphasises nothing.

---

## Core components

- **Cover card** — 2:3 image, 12 dp radius. Unread count: blue pill, white text, top-right. Type
  chip (`Anime` / `Manga`): small surface-colour chip, top-left, **shown only under the All lens**.
- **Continue card** — cover card + 3 dp blue progress bar flush at the bottom edge + one-line title
  below + caption `Ch. 254 · 2h ago` / `Ep. 12 · yesterday` + `NEW` pill (Accent) when something
  newer than the position exists.
- **Lens button** — one top-bar icon: full outlined circle = All; half-shaded blue circle =
  filtered to Anime or Manga. Tap opens a three-item menu (All / Anime / Manga, check on current,
  caption *Applies everywhere*). No text label accompanies the icon.
- **Update row** — 48 dp thumbnail (12 dp radius), title (15 Medium), one caption line
  (`Chapter 1187 · 2m ago`), trailing NEW pill or download glyph.
- **Buttons** — primary: blue fill, full-round, white 15 Medium label; secondary: outlined,
  white label. One primary per screen.
- **Empty state** — one brand illustration (halftone burst / speed-line sweep / ink splash), one
  sentence, **one button**. Never an empty screen without a verb.
- **Bottom bar** — five tabs: Home, Library, Discover, Updates, Downloads. Single-word labels,
  never truncated.
- **Filter sheet** — one bottom sheet, three titled sections: Sort (radio), Filter (checkboxes),
  Display (columns segmented + toggles).

---

## The lens — the one concept the design must teach

Three states, **one global value**: flip it on Home and Library arrives already flipped. It renders
as the **lens button** in the top bar of Home, Library, Discover, Updates and Search — the icon
itself is the state (full circle = All, half-shaded blue = filtered). Under `Anime` or `Manga`,
type chips vanish from covers (redundant); under `All` they appear. Mock at least one pair of
screens that shows the lens carrying over.

---

## Screens to design, in priority order

Real-ish content throughout (e.g. Solo Leveling, One Piece, Jujutsu Kaisen, Kingdom, Frieren,
Chainsaw Man). Show dark theme by default.

1. **Home** — top bar (wordmark left, search, lens button, settings) · *Continue* rail ·
   *Latest updates* list (5 rows + See all). No stat chips — Home is what you were in the middle
   of, and what arrived since. **States:** populated · empty (single card + Discover button) ·
   **RTL Arabic variant**.
2. **Library** — title, search, lens button, filter icon · category chips · 3-column cover grid.
   **States:** All lens (type chips visible) · Anime lens · filter sheet open.
3. **Discover** — search field · lens button · *Trending now* rail · *This season / Popular now* ·
   *Top rated* · *Your sources* cards · Manage row. State: works-with-zero-sources (rails full,
   sources row empty with "Add sources" card).
4. **Title page** — blurred backdrop header, cover, title/author/source/status · resume-aware
   primary (`Resume · Ch. 184`) · heart (In library), tracking ring, WebView, share · tabs:
   About / Chapters / Tracking / Also on. **Two variants:** manga (chapter rows + sticky range
   toolbar + fast scrubber) and anime (episode rows with thumbnails).
5. **Onboarding** — six screens: brand moment · Anime/Manga/Both · content languages (Arabic
   prominent) · sources explainer (official-portal names as suggestions + "paste a repo URL"
   field, skippable) · bring your history (Aniyomi import / tracker sign-in / start fresh) ·
   done → Discover.
6. **Sources & extensions** — one screen, segments Installed · Available · lens button · source
   rows (icon, name, language, type chip, pin, gear, Update pill) · Repositories row on top.
7. **Settings root** — ten entries with subtitles: Appearance · Library · **Reading** ·
   **Watching** · Sources · Downloads & storage · Tracking · Backup & data · Privacy & security ·
   Advanced/About. (Reading and Watching are siblings — this symmetry is the point.)
8. **Updates** — lens button · day-grouped feed · rows with NEW pill and download glyph · swipe
   affordances.
9. **Downloads** — storage header (`1.2 GB in downloads · Clean up`) · Active with progress ·
   Queued · Failed.
10. **Search** — one field, the lens button, results grouped *In your library* then *From your sources*.
11. **Splash** — Ink Black, centered unframed wordmark (blue ANIMATO + white アニマト, speed-line
    accents), no panel, no spinner. Light variant on Paper.

### Arabic labels for the RTL mock

الرئيسية (Home) · المكتبة (Library) · استكشاف (Discover) · التحديثات (Updates) · التنزيلات
(Downloads) · متابعة (Continue) · مكتبتك (Your library) · آخر التحديثات (Latest updates) ·
الكل / أنمي / مانجا (All / Anime / Manga) · بحث (Search).

---

## Do / Don't

**Do:** Animato Blue for primary actions only · strong contrast · clear logo space · manga DNA in
moments · one primary button per screen · real content in mocks · label every icon in the bottom bar.

**Don't:** stretch or recolour the logo · gradients on brand elements · blue as decoration ·
kaomoji empty states · dropdowns where chips fit · two chip rows stacked on one screen · media
words (Chapter/Read) in shared UI that also covers anime.

---

## Deliverable format — read this before exporting

The app is **Jetpack Compose (Kotlin), not a web app**. HTML/CSS will not be pasted in; it will be
**read as a machine-readable spec** and rebuilt in Compose. That makes HTML/CSS the *preferred*
deliverable — better than images — because exact values survive:

- One **HTML file per screen**, inline CSS, artboard 393 px wide, dark theme default.
- Use the exact hex tokens above; put spacing values in the CSS, not baked into images.
- Real text as text (not rasterised) so copy and sizes are extractable.
- Keep it static — no JS needed; hover states are meaningless on Android.

What transfers one-to-one into Compose: colours, type scale, spacing, radii, layout structure,
copy, iconography choices. What gets re-created rather than converted: the code itself, animations,
scroll behaviour. Screens delivered as images are workable too — values just get eyeballed instead
of read, which is slower and less exact.
