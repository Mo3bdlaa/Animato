# design.md — Animato working style guide

**v1.1.** The always-loaded rules file for any design tool or designer working on Animato screens.
Terse on purpose: this is the sheet you re-check every screen against. Screen-by-screen requirements
and priorities live in `DESIGN_BRIEF.md`; product reasoning lives in `UX.md`.

> v1.1 folds in every value the design pass had to invent, so no screen depends on an annotation.
> All additions are marked **[v1.1]** and listed again in the changelog at the bottom.

## Identity

One Android app for anime **and** manga: one library, one home, one global lens. Dark, confident,
manga-inflected but quiet about it. Tagline: *Your anime & manga universe, unified. Read. Watch.
Track.*

## Frame

- Android phone, portrait, artboard **393 × 852 dp**. Touch targets ≥ 48 dp.
- **Dark theme is the default identity.** Light "Paper" variant is secondary.
- Material 3 bones (bottom bar, sheets, switches); brand skin on top.
- RTL/Arabic is first-class — mirrored layouts must work.
- No web idioms: no hover, no cursor, no JS.

## Colour tokens

| Token | Hex | Use |
| --- | --- | --- |
| Animato Blue | `#4169A1` | Active states, primary action, progress — **nothing else** |
| Ink Black | `#08080C` | Dark background |
| Surface | `#151516` | Cards, sheets, bars on dark |
| Paper | `#F2EEE5` | Light background |
| **Surface Light** **[v1.1]** | `#FFFFFF` | Cards, sheets, bars **on Paper** |
| Muted | `#9A9690` | Secondary text, inactive icons |
| White | `#FFFFFF` | Primary text on dark |
| **Ink Text** **[v1.1]** | `#08080C` | Primary text on Paper |
| Success | `#22C55E` | Done, synced, downloaded |
| Warning | `#F59E0B` | Pending, attention, **extension update available** **[v1.1]** |
| Error | `#EF4444` | Failure |
| Info | `#8B5CF6` | Informational accent |
| Accent | `#06B6D4` | NEW pill; rare highlights |

**Hairlines and tracks:** `#FFFFFF14` on dark, **`#08080C14` on Paper [v1.1]**. Same rule for
progress-bar tracks, dividers, 1 dp inset borders and switch-off tracks.

**The blue rule:** blue appearing in more than ~3 places on a screen means the screen is wrong.
Corollary **[v1.1]**: a screen with nothing active or in progress — a settings list, a source list —
is allowed **zero** blue. Do not add blue to prove a screen is alive.

## Type

Noto Sans everywhere; Noto Sans JP only for brand moments (splash, onboarding, about).
**Arabic renders through fallback [v1.1]:** declare `'Noto Sans','Noto Sans Arabic'` so Latin series
titles keep Noto Sans and only Arabic strings switch face. Never translate a series title.

| Role | Size/weight |
| --- | --- |
| Screen title | 24 Bold |
| Section header | 18 SemiBold |
| Card title | 15 Medium |
| Body | 15 Regular |
| Caption / secondary | 13 Regular, Muted |
| Chip label | 14 Medium |
| Tab label | 12 Medium |
| **Pill label (NEW, type chip, Update)** **[v1.1]** | 11 / 600 in a 20 dp pill, 7 dp side padding |
| **Group header inside a list** **[v1.1]** | 13 SemiBold, Muted, 40 dp row |

Numbers (chapter counts, sizes, percentages, queue positions) are tabular.

## Space & shape

- 4 dp grid. Screen padding 16. Rail card gap 12. Section gap 24.
- Cover cards 2:3, radius 12. Chips full-round. Sheets 28 top radius.
- Progress bars 3 dp, blue on the track colour, square ends, flush to card bottom.

### Sizes **[v1.1]**

| Element | Value |
| --- | --- |
| Continue card (Home rail) | 148 × 222, r12, title + caption **on** the cover over an ink scrim |
| Library grid cover | 112 × 168, r12, 3-up (16 + 3×112 + 2×12 + 16 = 393) |
| Search source-result card | 104 × 156, r12, 2-line title below (40 dp reserved) |
| Title-page cover | 112 × 168 on a 260 dp blurred backdrop |
| Episode thumb | 96 × 54 (16:9), r8 |
| Update / queue / result thumb | 48 × 48, r12 |
| Source icon | 40 × 40, r12 — a logo, not artwork |
| List row (update, queue, search result) | 56–72 dp |
| Settings row · onboarding option row | 72 dp |
| Search field · primary button | 48 dp, full-round |
| Reader page scrubber | 4 dp track, 44 dp thumb, tabular label |
| Onboarding step indicator | 6 dp dots, active 16 × 6 pill, 8 dp gap |
| Queue position numeral | 13 Regular tabular in a 24 dp box |

## Icons

Outlined 24 dp, one stroke weight. Active bottom-nav tab = filled + blue; inactive = outlined +
muted. Never duotone, never colourful.

**RTL mirroring [v1.1]:** mirror direction-bearing glyphs only — back/forward chevrons, the book-shaped
Library glyph, the refresh arc. Circles, download arrows, gears, hearts and the lens circle never
mirror. The wordmark never mirrors.

## Components

- **Cover card** — unread pill top-right (blue, white count). Type chip (`Anime`/`Manga`) top-left
  **only under the All lens**.
- **Continue card** — cover card + 3 dp progress bar + title and caption `Ch. 254 · 2h ago` set on
  the cover over an ink scrim + Accent `NEW` pill when applicable. Tap = opens content, not the
  title page.
- **Lens button** — one top-bar icon, 24 dp, on Home, Library, Discover, Updates, Search.
  Full outlined circle = All; the same circle **half-shaded and blue** = filtered to Anime or
  Manga. Tap opens a small menu — All / Anime / Manga with a check on the current state and the
  caption *Applies everywhere*. Choosing All restores the full circle. **No text label ever
  accompanies the icon.**
- **Update row** — 48 dp thumb (r12), 15 Medium title, 13 Muted caption, trailing NEW/download glyph.
- **Buttons** — primary blue fill full-round; secondary outlined. **One primary per screen.**
- **Segments** **[v1.1]** — where a screen splits into two lists (Installed / Available, title-page
  tabs): 15 Medium label, 2 dp blue underline on the active one. **Never chips** — chips are the
  lens/category component and two chip-shaped rows on one screen is a violation.
- **Settings row** **[v1.1]** — 72 dp, 24 dp icon, 15 Medium title, 13 Muted subtitle that names
  what is inside. **No chevron**: the row is the target.
- **Storage line** **[v1.1]** — one row, number + count as caption, outlined action pill trailing.
  No charts of the user's own storage.
- **Empty state** — brand illustration (halftone burst / speed-line sweep / ink splash) + one
  sentence + **one button**. Kaomoji are banned. The sentence names the **cause** when there is one
  (*You don't have any anime sources yet*), never a shrug **[v1.1]**.
- **Bottom bar** — Home · Library · Discover · Updates · Downloads. Single words, never truncated.
  Sub-screens (Sources, Settings, Repositories, Tracking) have **no** bottom bar **[v1.1]**.
- **Filter sheet** — one sheet, three sections: Sort (radio) · Filter (checkbox) · Display.

## The lens

Three states — All · Anime · Manga — one **global** value: flipping it anywhere flips it
everywhere. It lives as the **lens button** in the top bar (see Components), never as a chip row:
the icon itself carries the state, half-shaded when you are looking at part of the collection.
Under `Anime`/`Manga` the type chips on covers disappear; under `All` they appear.

**One exception [v1.1]:** on *Sources & extensions* the type chip is on **every** row regardless of
lens — that screen exists to tell anime and manga extensions apart, so the chip cannot be a thing
that disappears. The lens still filters which rows are listed.

## Categories vs derived states **[v1.1]**

Library's chip row is **user categories** — *Ongoing · Backlog · Finished* are the three seeded at
first run, renameable in Settings › Library › Categories; the row renders only when more than one
category exists. Derived states — *Unread · Downloaded · Started · Tracked* — live in the filter
sheet, because a title can be in several at once and none of them is a shelf.

## Manga DNA

Speed lines, halftone dots, ink texture, bold panel angles — allowed in **splash, onboarding,
empty states, celebrations**. Forbidden in toolbars, lists, grids, settings.

## Copy

- Shared UI uses neutral words: *Continue, In library, Updated, Opened, In progress, Done.*
- *Chapter/Episode, Read/Watch* only inside a single-medium context.
- The heart means **In library** — the word "favorite" never appears.
- Failures state the reason in words: *Source returned 403 · check the source* **[v1.1]**.
- Arabic labels: الرئيسية · المكتبة · استكشاف · التحديثات · التنزيلات · متابعة · مكتبتك ·
  آخر التحديثات · الكل / أنمي / مانجا · بحث.
- Arabic added in v1.1: جارية / لاحقًا / منتهية (categories) · في مكتبتك / من مصادرك (search) ·
  جارٍ التنزيل / في الطابور / فشلت · تنظيف · إعادة المحاولة · عرض الكل · جديد · الفصل / الحلقة.

## Do / Don't

**Do:** exact tokens · strong contrast · real content in mocks (Solo Leveling, One Piece, Jujutsu
Kaisen, Kingdom, Frieren, Chainsaw Man) · label all bottom-bar icons · one primary action.

**Don't:** recolour/stretch/gradient the logo · blue as decoration · dropdowns where chips fit ·
two chip rows stacked · media words in shared UI · invent new tokens, sizes or colours — if it is
not in this file, ask.

## Output contract

One HTML file per screen. Inline CSS. 393 px artboard, dark default. Exact hex from this file.
Real text as text (nothing rasterised). Static — no JS. This HTML is read later as a
machine-readable spec and rebuilt in Jetpack Compose, so values in CSS matter more than pixels.

## Changelog — v1.1

Added because a screen needed them and the value was not in v1.0:

1. Surface Light `#FFFFFF` and Ink Text `#08080C` — the Paper variant had no surface or text token.
2. Paper hairline/track `#08080C14` — the mirror of `#FFFFFF14`.
3. Zero-blue corollary to the blue rule.
4. Arabic via font fallback; never translate series titles; RTL mirroring list.
5. Pill label (11/600, 20 dp) and in-list group header (13 SemiBold, 40 dp).
6. The whole **Sizes** table, including Continue 148 × 222, source icon 40, settings row 72,
   search field 48, episode thumb 96 × 54, scrubber 4/44, step indicator, queue numeral.
7. **Segments** component — and the ban on using chips for it.
8. Settings row without chevron; storage line without charts.
9. Empty states must name the cause; failures state the reason.
10. Sub-screens carry no bottom bar.
11. Warning `#F59E0B` extended to the extension **Update** pill.
12. The Sources type-chip exception to the lens rule.
13. Categories vs derived states.
14. Home's stat chips deleted outright (decision from round two): Home is Continue + Latest updates.
