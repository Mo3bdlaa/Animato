package animato.anime.util

import coil3.Extras
import coil3.getExtra
import coil3.request.ImageRequest
import coil3.request.Options

/**
 * Marks an image request as loading an anime's *background* art rather than its cover.
 *
 * Anime entries carry two images — the portrait cover and the wide backdrop behind the details
 * header — and both are fetched through the same Coil fetcher for the same `Anime`. The fetcher
 * needs to know which one is being asked for, and Coil's way of carrying that is an extra on the
 * request.
 *
 * Aniyomi put this in Mihon's `data/coil/Utils.kt`, where it was `internal` — visible to its
 * callers only because they were in the same module. The anime screens are not, so it lives here.
 * The key stays private: a request opts in through [useBackground] and a fetcher reads it back
 * through the `Options` property, and nothing else should be setting it directly.
 */
private val useBackgroundKey = Extras.Key(default = false)

fun ImageRequest.Builder.useBackground(enable: Boolean) = apply {
    extras[useBackgroundKey] = enable
}

val Options.useBackground: Boolean
    get() = getExtra(useBackgroundKey)
