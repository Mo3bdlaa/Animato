package animato.domain.content

/**
 * Which kinds of content the unified library should show.
 *
 * Aniyomi split anime and manga across two library tabs, which forced a choice on every visit and
 * left people who only care about one of them permanently looking at the other. Making this a
 * filter over one library turns that split into a preference instead of a layout.
 */
enum class ContentFilter {
    ALL,
    ANIME,
    MANGA,
    ;

    val includesAnime: Boolean get() = this != MANGA

    val includesManga: Boolean get() = this != ANIME

    fun accepts(type: ContentType): Boolean = when (type) {
        ContentType.ANIME -> includesAnime
        ContentType.MANGA -> includesManga
    }
}
