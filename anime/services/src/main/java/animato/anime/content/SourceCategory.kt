package animato.anime.content

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.animesource.model.AnimesPage

/**
 * One way a source divides up what it holds.
 *
 * A playlist's `group-title`. A Stremio catalog, and each genre inside it. The word people already
 * use for these is *category*, and until now the app had no word for them at all — they arrived as
 * anonymous `Select` filters and were rendered as dropdowns in a sheet.
 */
@Immutable
data class SourceCategory(
    /**
     * Opaque, and the source's own business.
     *
     * The screen carries it back unread. A Stremio category has to remember which catalog it came
     * from as well as which genre it is, and a group name is enough on its own; making the screen
     * understand either would put addon parsing in a composable.
     */
    val id: String,
    val label: String,
    /**
     * The set this belongs to, when a source publishes more than one — a Stremio genre belongs to
     * the catalog that declared it, and two catalogs can both have an *Action*.
     *
     * Null for a flat list, which is what an M3U playlist has and what most things have.
     */
    val group: String? = null,
)

/**
 * A source whose divisions are worth putting on screen.
 *
 * ## Why this is not the filter list
 *
 * Both sources that need it already published their categories as filters, and both were unusable
 * that way. A playlist's groups are a `Select` of four hundred entries inside a sheet that has to
 * be opened, scrolled, chosen from and applied — four gestures to reach *Sport*, which is how
 * anybody who has ever used an IPTV app expects to start. A Stremio addon's genres are worse
 * still: they are declared per catalog, so the sheet holds one dropdown per catalog and choosing
 * from the wrong one silently does nothing.
 *
 * The filter sheet is not being replaced. It is the right shape for what it holds — sorts,
 * tri-states, freeform text, the tree an extension composes for itself. Categories are one choice
 * from one list, they are the choice people make first, and they belong where the thumb is.
 *
 * ## Why it is anime-only
 *
 * Both implementations are, and no manga source has anything like it: a scraper extension's genres
 * are a filter tree we cannot read without guessing at names. An interface with one side
 * unimplemented would be a promise the app does not keep.
 */
interface BrowsableByCategory {
    /**
     * Everything this source can be divided by, in the order it should be offered.
     *
     * Suspending because a playlist has to be read before its groups are known, and returning an
     * empty list from a cache is how the group picker came to be missing on the first browse after
     * a restart. Called once when the screen opens.
     */
    suspend fun categories(): List<SourceCategory>

    /** One page of [categoryId], which is an id this source itself produced. */
    suspend fun browseCategory(categoryId: String, page: Int): AnimesPage
}
