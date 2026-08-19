package animato.app.stremio

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

/**
 * A few addons worth having, so the first one is not a search.
 *
 * Adding an addon is adding a URL, which is only simple once you know which URL. From a device,
 * twice: the manifest link pasted into the extension-store dialog, and then the question of where
 * to find a Torrentio address at all. Somebody who has just installed the app has no way to know
 * that a catalogue and a stream provider are different addons, let alone what either is called.
 *
 * Four, not forty. This is a starting point rather than a directory: one anime catalogue, one
 * general one, one source of video and one of subtitles — which between them is a working setup,
 * and past that people know what they are looking for.
 *
 * Tapping one fills the address field rather than installing straight away. That looks like an
 * extra tap and is not: the address stays visible and editable, which is what Torrentio needs
 * (its useful form is configured on its own page and carries the settings in the path) and what
 * makes the whole thing legible as "an addon is a URL" rather than as a private app store.
 */
data class SuggestedAddon(
    val name: String,
    val url: String,
    val description: StringResource,
)

/**
 * Ordered anime-first, because this is an anime and manga app and the list is also a statement
 * about what it is for. Cinemeta is second rather than first for the same reason: it is the
 * catalogue everyone in the Stremio world starts with, and it is full of films.
 */
val SUGGESTED_ADDONS = listOf(
    SuggestedAddon(
        name = "Anime Kitsu",
        url = "https://anime-kitsu.strem.fun/manifest.json",
        description = AYMR.strings.stremio_suggested_kitsu,
    ),
    SuggestedAddon(
        name = "Cinemeta",
        url = "https://v3-cinemeta.strem.io/manifest.json",
        description = AYMR.strings.stremio_suggested_cinemeta,
    ),
    SuggestedAddon(
        name = "Torrentio",
        url = "https://torrentio.strem.fun/manifest.json",
        description = AYMR.strings.stremio_suggested_torrentio,
    ),
    SuggestedAddon(
        name = "OpenSubtitles v3",
        url = "https://opensubtitles-v3.strem.io/manifest.json",
        description = AYMR.strings.stremio_suggested_opensubtitles,
    ),
)
