package animato.domain.content

/**
 * The kind of content a library entry holds.
 *
 * Animato presents anime and manga in a single library, so the type has to travel with each entry
 * rather than being implied by which screen you happen to be looking at.
 */
enum class ContentType {
    ANIME,
    MANGA,
    ;

    val isAnime: Boolean get() = this == ANIME
    val isManga: Boolean get() = this == MANGA
}
