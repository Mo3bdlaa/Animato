package animato.domain.content

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * The lens: which half of the app the user is currently looking at.
 *
 * Animato has one set of destinations rather than one per content type, so "am I looking at anime
 * or manga" is a lens over the whole app rather than a place in it. Home, Library, Discover,
 * Updates and Search all read this one value, which is the point: change it anywhere and every
 * other screen agrees when you arrive.
 *
 * It is persisted because it is a preference in the ordinary sense — someone who only watches anime
 * should not have to say so again every launch.
 *
 * ## Why there is one value here and there used to be two
 *
 * This was a pair: a `ContentType` for the destinations that can only draw one half, and a
 * `ContentFilter` with an `ALL` for the library, which can draw both. Two stored answers to one
 * question, kept in step by hand — and they came apart exactly where you would expect. The home
 * screen's switch wrote one of them while its Continue rail read neither, so the toggle could say
 * Anime over a list of manga chapters.
 *
 * So the filter is the whole of it. A screen that cannot draw both halves resolves `ALL` to a
 * default when it renders, without writing that choice back.
 */
class ContentPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * A new key rather than a migration of the old pair.
     *
     * The old `animato_content_type` could not express `ALL`, so there is nothing faithful to carry
     * across: every existing install would have to be assigned a third state it never chose. `ALL`
     * is both the honest default and the one people want — one library is the product.
     */
    val contentFilter = preferenceStore.getEnum("animato_content_lens", ContentFilter.ALL)

    /**
     * Entries dismissed from Home's Continue rail, as `TYPE:id` strings.
     *
     * A preference and not a column, because the fact being stored is about the *rail*, not the
     * entry: "stop offering me this here" — the entry keeps its history, and keeps its place in
     * the library if it is favourited. Watching or reading it again writes a newer history row,
     * and the dismissal is dropped then, because whoever opened it plainly changed their mind.
     */
    val hiddenFromContinue = preferenceStore.getStringSet("animato_hidden_from_continue", emptySet())

    /**
     * Extension packages whose NSFW-by-default incognito has already been applied once.
     *
     * The seeding is once per package, not continuous, so that switching incognito *off* for an
     * NSFW extension is a decision the app respects instead of silently reverting on the next
     * launch. See `NsfwDefaults`.
     */
    val nsfwIncognitoSeeded = preferenceStore.getStringSet("animato_nsfw_incognito_seeded", emptySet())
}
