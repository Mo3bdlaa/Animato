package animato.app.discover

import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.manga.model.MangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The last answer each Discover rail gave, kept so the next open starts with it.
 *
 * From a device: *"trending and my sources take a while to load — cache them, show the cached
 * ones, and let the update see if there is anything new or leave them."* Which is exactly the
 * shape here: rails render their previous contents immediately, the fetch still runs, and a fresh
 * answer replaces the shelf while a failed one leaves it standing — a stale shelf beats an empty
 * one wearing a spinner.
 *
 * Stored as JSON strings in the preference store rather than a table: this is a render cache of a
 * few dozen titles per rail, not data — losing it costs one slow open, and nothing else in the
 * app is allowed to read it.
 */
class DiscoverCache(
    private val preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun saveMetadata(railKey: String, items: List<MetadataItem>) {
        val cached = items.map {
            CachedRailItem(key = it.key, title = it.title, coverUrl = it.coverUrl, caption = it.caption)
        }
        preferenceStore.getString(metadataKey(railKey)).set(json.encodeToString(cached))
    }

    fun loadMetadata(railKey: String, contentType: ContentType): List<MetadataItem> {
        return decode(metadataKey(railKey)).map {
            MetadataItem(
                key = it.key,
                title = it.title,
                coverUrl = it.coverUrl,
                caption = it.caption,
                contentType = contentType,
            )
        }
    }

    fun saveSourceRail(name: String, lens: ContentFilter, items: List<DiscoverItem>) {
        val cached = items.map {
            CachedRailItem(
                key = it.key,
                title = it.title,
                coverUrl = it.coverData.coverUrl(),
                sourceId = it.sourceId,
                url = it.url,
                type = it.contentType.name,
            )
        }
        preferenceStore.getString(sourceKey(name, lens)).set(json.encodeToString(cached))
    }

    fun loadSourceRail(name: String, lens: ContentFilter): List<DiscoverItem> {
        return decode(sourceKey(name, lens)).mapNotNull {
            val contentType = runCatching { ContentType.valueOf(it.type ?: return@mapNotNull null) }
                .getOrNull() ?: return@mapNotNull null
            DiscoverItem(
                key = it.key,
                title = it.title,
                // A real cover object, not a bare URL: many sources serve covers only to requests
                // carrying their own headers, and the fetchers key that behaviour off these types.
                // The placeholder id is fine — id −1 has no custom cover and is nobody's favourite.
                coverData = when (contentType) {
                    ContentType.MANGA -> MangaCover(
                        mangaId = -1L,
                        sourceId = it.sourceId ?: 0L,
                        isMangaFavorite = false,
                        url = it.coverUrl,
                        lastModified = 0L,
                    )
                    ContentType.ANIME -> AnimeCover(
                        animeId = -1L,
                        sourceId = it.sourceId ?: 0L,
                        isAnimeFavorite = false,
                        url = it.coverUrl,
                        lastModified = 0L,
                    )
                },
                sourceId = it.sourceId ?: 0L,
                url = it.url ?: return@mapNotNull null,
                contentType = contentType,
            )
        }
    }

    private fun decode(key: String): List<CachedRailItem> {
        val raw = preferenceStore.getString(key).get()
        if (raw.isEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<CachedRailItem>>(raw) }.getOrDefault(emptyList())
    }

    private fun Any.coverUrl(): String? = when (this) {
        is MangaCover -> url
        is AnimeCover -> url
        is SAnime -> thumbnail_url
        is String -> this
        else -> null
    }

    private fun metadataKey(railKey: String) = "animato_discover_cache_meta_$railKey"
    private fun sourceKey(name: String, lens: ContentFilter) = "animato_discover_cache_src_${name}_${lens.name}"

    @Serializable
    private data class CachedRailItem(
        val key: String,
        val title: String,
        val coverUrl: String? = null,
        val caption: String? = null,
        val sourceId: Long? = null,
        val url: String? = null,
        val type: String? = null,
    )
}
