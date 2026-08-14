# Upstream divergence log

Things Aniyomi relied on that Mihon has since changed, removed or replaced — recorded as they turn
up during the port, so the decision can be revisited deliberately instead of forgotten.

Each entry answers three questions: **what we use it for**, **what Mihon did**, and **whether their
reason is a better idea we should adopt**. The last one matters most: a removal upstream is often a
lesson, not an obstacle. Reintroducing something Mihon deleted for good reason means carrying a
mistake they already fixed.

## Status key

- **adopted** — we took Mihon's newer approach
- **carried** — we kept Aniyomi's version, with a reason
- **open** — needs a decision

---

## Adopted

### `PreferenceStore.getObject` → `getObjectFromString`

Used for preferences holding a serialised object: library sort mode, display mode.

A rename with an identical signature. We had written a replacement before noticing; it was deleted
and Mihon's used instead. A file we do not own is a file we do not maintain.

### Preferences: functions → properties

`libraryPreferences.displayMode()` became `libraryPreferences.displayMode`.

A plain modernisation with no behavioural change. Our anime preference classes should follow the
same shape so both halves read alike — **not yet done**, `AnimeLibraryPreferences` still exposes
functions.

### `MemoColumnAdapter`

Stores a JSON object as bytes in the database.

Aniyomi wrote one; Mihon has since grown its own, behaviourally identical. Ours deleted, theirs
used.

### `source-api` as a multiplatform module

Mihon collapsed it to a plain Android library. `:anime:source-api` followed, since a common source
set cannot depend on an Android-only one. The one `expect`/`actual` pair collapsed to the Android
side, keeping the `PreferenceScreen` alias intact because extensions compile against that name.

---

### `SmallExtendedFloatingActionButton`

Aniyomi carried a copy in `presentation-core`. It has since graduated into Material 3 proper, and
Mihon uses the framework one. Ours now imports `androidx.compose.material3` — one fewer file to own.

---

## Open

### `CategoryUpdate` — deleted by Mihon

Used by every category interactor to apply a partial update.

Mihon deleted the class. **What replaced it has not been established.** We kept our own copy — as
`AnimeCategoryUpdate`, in a package of ours — so the port could proceed, but if Mihon replaced it
with something better shaped, ours should follow.

*To check: how Mihon's category interactors apply partial updates now.*

### `kotlinx-collections-immutable` — dropped by Mihon

Our ported components take `ImmutableList` and `persistentListOf` in composable parameters. Mihon
now uses it in only two files and dropped the dependency from its catalogues; we added it to ours
to keep the port mechanical.

The likely reason is Compose strong skipping, which made immutable collection types largely
unnecessary for recomposition stability. If so this is a genuine simplification: plain `List`
everywhere, one dependency fewer, and no behaviour change.

*To check: whether strong skipping is on in Mihon's Compose configuration. If it is, drop the
dependency and use `List`.*

### `GetLibraryManga` — moved

Needed by the unified library, which reads both halves at once. Mihon moved it out of
`tachiyomi.domain.entries.manga.interactor`; it now lives under `tachiyomi.domain.manga`, and the
`entries` layer appears to be gone entirely.

The unified library interactor is parked until the screen that uses it is built, so this is
unresolved by design rather than by neglect.

---

## Not upstream changes — our own boundary violations

Recorded here because they surfaced the same way, as compile errors during the port, but the cause
is different: Aniyomi had added these *inside* Mihon's files.

| Thing | Now |
| --- | --- |
| `Category.hidden` | `animato.domain.category.AnimeCategory` — anime categories were always a separate table with a separate id space |
| `EntryCover` | `animato.domain.entries.EntryCover` |
| anime column adapters in `tachiyomi.data` | `animato.data` — never put a class in a package upstream owns, even when it does not collide today |
| `DownloadAction` | Mihon has its own in `MangaScreenConstants`; ours lives in `animato.ui.entries` and the two are independent |
