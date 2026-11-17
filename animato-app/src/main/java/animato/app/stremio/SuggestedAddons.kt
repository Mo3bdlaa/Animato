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
 * A handful, not the whole directory: catalogues, sources of video and subtitles that answered
 * without an account when this list was written. The full directory is a search away.
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
        name = "Anime Catalogs",
        url = "https://1fe84bc728af-stremio-anime-catalogs.baby-beamup.club/manifest.json",
        description = AYMR.strings.stremio_suggested_anime_catalogs,
    ),
    SuggestedAddon(
        name = "Cinemeta",
        url = "https://v3-cinemeta.strem.io/manifest.json",
        description = AYMR.strings.stremio_suggested_cinemeta,
    ),
    SuggestedAddon(
        name = "The Movie Database",
        url = "https://94c8cb9f702d-tmdb-addon.baby-beamup.club/manifest.json",
        description = AYMR.strings.stremio_suggested_tmdb,
    ),
    SuggestedAddon(
        name = "Streaming Catalogs",
        url = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json",
        description = AYMR.strings.stremio_suggested_streaming_catalogs,
    ),
    SuggestedAddon(
        name = "Torrentio",
        url = "https://torrentio.strem.fun/manifest.json",
        description = AYMR.strings.stremio_suggested_torrentio,
    ),
    SuggestedAddon(
        name = "Comet",
        url = "https://comet.elfhosted.com/manifest.json",
        description = AYMR.strings.stremio_suggested_comet,
    ),
    SuggestedAddon(
        name = "MediaFusion",
        url = "https://mediafusion.elfhosted.com/manifest.json",
        description = AYMR.strings.stremio_suggested_mediafusion,
    ),
    SuggestedAddon(
        name = "OpenSubtitles v3",
        url = "https://opensubtitles-v3.strem.io/manifest.json",
        description = AYMR.strings.stremio_suggested_opensubtitles,
    ),
)

/**
 * Public M3U playlists that answered when this list was written, offered under the live-TV heading.
 * iptv-org's are maintained lists of free channels, split by language, country and category; the
 * whole of it is there too, for somebody who wants everything and a long load.
 */
val SUGGESTED_PLAYLISTS = listOf(
    SuggestedAddon(
        name = "iptv-org · Arabic",
        url = "https://iptv-org.github.io/iptv/languages/ara.m3u",
        description = AYMR.strings.iptv_suggested_arabic,
    ),
    SuggestedAddon(
        name = "iptv-org · Egypt",
        url = "https://iptv-org.github.io/iptv/countries/eg.m3u",
        description = AYMR.strings.iptv_suggested_egypt,
    ),
    SuggestedAddon(
        name = "iptv-org · Movies",
        url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
        description = AYMR.strings.iptv_suggested_movies,
    ),
    SuggestedAddon(
        name = "iptv-org · Animation",
        url = "https://iptv-org.github.io/iptv/categories/animation.m3u",
        description = AYMR.strings.iptv_suggested_animation,
    ),
    SuggestedAddon(
        name = "iptv-org · Kids",
        url = "https://iptv-org.github.io/iptv/categories/kids.m3u",
        description = AYMR.strings.iptv_suggested_kids,
    ),
    SuggestedAddon(
        name = "iptv-org · Sports",
        url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
        description = AYMR.strings.iptv_suggested_sports,
    ),
    SuggestedAddon(
        name = "iptv-org · News",
        url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        description = AYMR.strings.iptv_suggested_news,
    ),
    SuggestedAddon(
        name = "iptv-org · Documentary",
        url = "https://iptv-org.github.io/iptv/categories/documentary.m3u",
        description = AYMR.strings.iptv_suggested_documentary,
    ),
    SuggestedAddon(
        name = "Free-TV",
        url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
        description = AYMR.strings.iptv_suggested_freetv,
    ),
    SuggestedAddon(
        name = "iptv-org · Everything",
        url = "https://iptv-org.github.io/iptv/index.m3u",
        description = AYMR.strings.iptv_suggested_all,
    ),
)
