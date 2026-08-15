package animato.ui.deeplink

/**
 * Which library a deep link is asking to search.
 *
 * Animato registers two deep-link activities, one per library, and both hand off to the single main
 * activity. The main activity cannot tell them apart from the intent alone — one that arrived
 * through the anime filter and one that arrived through the manga filter look identical by the time
 * it reads them.
 *
 * So the anime one stamps the intent on the way past, and only the anime one: the manga activity is
 * Mihon's and stamps nothing, which is what makes an unstamped intent mean manga rather than an
 * unanswered question.
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
