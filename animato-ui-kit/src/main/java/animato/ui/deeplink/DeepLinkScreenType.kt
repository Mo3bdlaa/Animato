package animato.ui.deeplink

/**
 * Which library a deep link is asking to search.
 *
 * Animato registers two deep-link activities, one per library, and both hand off to the single main
 * activity. The main activity cannot tell them apart from the URL alone — an intent that arrived
 * through the anime filter and one that arrived through the manga filter look the same by the time
 * it reads them — so each stamps the intent with its own type on the way through.
 *
 * The extra key lives here rather than on the main activity because the deep-link activities sit in
 * the library modules, and those cannot see the app module that hosts it.
 */
enum class DeepLinkScreenType {
    ANIME,
    MANGA,
    ;

    companion object {
        /** Intent extra naming the [DeepLinkScreenType] a deep link was routed through. */
        const val INTENT_SEARCH_TYPE = "type"
    }
}
