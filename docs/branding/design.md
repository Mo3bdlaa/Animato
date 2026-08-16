# design.md — Animato working style guide

The always-loaded rules file for any design tool or designer working on Animato screens. Terse on
purpose: this is the sheet you re-check every screen against. Screen-by-screen requirements and
priorities live in `DESIGN_BRIEF.md`; product reasoning lives in `UX.md`.

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
| Muted | `#9A9690` | Secondary text, inactive icons |
| White | `#FFFFFF` | Primary text on dark |
| Success | `#22C55E` | Done, synced, downloaded |
| Warning | `#F59E0B` | Pending, attention |
| Error | `#EF4444` | Failure |
| Info | `#8B5CF6` | Informational accent |
| Accent | `#06B6D4` | NEW pill; rare highlights |

**The blue rule:** blue appearing in more than ~3 places on a screen means the screen is wrong.

## Type

Noto Sans everywhere; Noto Sans JP only for brand moments (splash, onboarding, about).

| Role | Size/weight |
| --- | --- |
| Screen title | 24 Bold |
| Section header | 18 SemiBold |
| Card title | 15 Medium |
| Body | 15 Regular |
| Caption / secondary | 13 Regular, Muted |
| Chip label | 14 Medium |
| Tab label | 12 Medium |

## Space & shape

- 4 dp grid. Screen padding 16. Rail card gap 12. Section gap 24.
- Cover cards 2:3, radius 12. Chips full-round. Sheets 28 top radius.
- Progress bars 3 dp, blue on `#FFFFFF14`, square ends, flush to card bottom.

## Icons

Outlined 24 dp, one stroke weight. Active bottom-nav tab = filled + blue; inactive = outlined +
muted. Never duotone, never colourful.

## Components

- **Cover card** — unread pill top-right (blue, white count). Type chip (`Anime`/`Manga`) top-left
  **only under the All lens**.
- **Continue card** — cover card + 3 dp progress bar + 1-line title + caption `Ch. 254 · 2h ago` +
  Accent `NEW` pill when applicable. Tap = opens content, not the title page.
- **Lens chips** — one segmented group `All · Anime · Manga`, always directly under the top bar,
  identical on Home, Library, Discover, Updates, Search. Active = blue fill.
- **Stat chip** — number 20 SemiBold over 13 Muted label, Surface, radius 12, tappable.
- **Update row** — 48 dp thumb (r12), 15 Medium title, 13 Muted caption, trailing NEW/download glyph.
- **Buttons** — primary blue fill full-round; secondary outlined. **One primary per screen.**
- **Empty state** — brand illustration (halftone burst / speed-line sweep / ink splash) + one
  sentence + **one button**. Kaomoji are banned.
- **Bottom bar** — Home · Library · Discover · Updates · Downloads. Single words, never truncated.
- **Filter sheet** — one sheet, three sections: Sort (radio) · Filter (checkbox) · Display.

## The lens

Three states, one **global** value: flipping it anywhere flips it everywhere. Under `Anime`/`Manga`
the type chips on covers disappear; under `All` they appear.

## Manga DNA

Speed lines, halftone dots, ink texture, bold panel angles — allowed in **splash, onboarding,
empty states, celebrations**. Forbidden in toolbars, lists, grids, settings.

## Copy

- Shared UI uses neutral words: *Continue, In library, Updated, Opened, In progress, Done.*
- *Chapter/Episode, Read/Watch* only inside a single-medium context.
- The heart means **In library** — the word "favorite" never appears.
- Arabic labels: الرئيسية · المكتبة · استكشاف · التحديثات · التنزيلات · متابعة · مكتبتك ·
  آخر التحديثات · الكل / أنمي / مانجا · بحث.

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
