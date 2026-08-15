package animato.anime.services

/**
 * The two pseudo-languages a source list groups by, alongside real ones.
 *
 * A source list is grouped by language, and these two headings sit above the real languages —
 * sources the user pinned, and the one they used last. They are string keys rather than an enum
 * because they occupy the same slot as a language code in the grouping, and they are these exact
 * strings because Aniyomi and Mihon both persist them.
 *
 * Mihon declares them `internal` next to its own sources view model, which the anime screens cannot
 * reach from another module. Copied rather than shared: they are stored values, and a string that
 * two modules must agree on is safer duplicated than imported across a boundary that upstream can
 * move without warning.
 */
const val PINNED_KEY = "pinned"

const val LAST_USED_KEY = "last_used"

/**
 * How long to wait after the last keystroke before searching.
 *
 * Every source search is a network request, so typing "one piece" without this fires nine of them
 * per source.
 */
const val SEARCH_DEBOUNCE_MILLIS = 250L
