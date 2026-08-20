package animato.anime.iptv

import animato.anime.content.EntryForm
import animato.anime.content.KnowsEntryForm
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * One M3U playlist, seen as one source.
 *
 * ## Why this is not a Stremio addon with a different fetcher
 *
 * An addon is a service you ask questions of: what is popular, what is this, where can I watch it.
 * A playlist is a *file* — every channel it will ever have arrives in one download, with no
 * search, no paging and no second request. So everything here works from one list held in memory,
 * and the parts of a source that exist to talk to a server are answered locally instead:
 *
 * - **Search** filters the list rather than asking anybody.
 * - **Paging** is slicing, because ten thousand channels in one response is a scroll that never
 *   ends and a grid that never settles.
 * - **Popular** and **Latest** are both the playlist's own order, which is the only order it has.
 *   A provider puts the channels people want at the top; nothing else in the file ranks anything.
 *
 * ## What a channel is here
 *
 * An entry with exactly one thing to play, which is *now*. It keeps no progress, is never marked
 * as seen and is fetched once — see [AnimeUpdateStrategy.ONLY_FETCH_ONCE] below and [formOf], which
 * between them mean a channel never behaves like a half-watched episode.
 */
class M3uSource(
    val playlist: M3uPlaylist,
) : AnimeHttpSource(), KnowsEntryForm {

    private val store: M3uPlaylistStore by injectLazy()

    /**
     * The playlist's address stands in for a site.
     *
     * [AnimeHttpSource] wants one and nothing here uses it for a request — every URL involved
     * comes out of the file itself — but the framework reads it in a few places, and pointing at
     * the playlist is the only honest answer available.
     */
    override val baseUrl: String = playlist.url

    override val name: String = playlist.name

    /** A playlist is whatever languages its provider put in it, which is rarely one. */
    override val lang: String = MULTI_LANG

    /**
     * Identity is the address, for the same reason the Stremio sources' is.
     *
     * A provider renames its playlist file's `#PLAYLIST` line whenever it likes, and a source
     * whose id follows the name takes everybody's library with it when that happens.
     */
    override val id: Long by lazy { idFor(playlist.url) }

    /**
     * There is one order and it is the file's, so a second shelf would be the same shelf twice.
     */
    override val supportsLatest: Boolean = false

    /**
     * Everything in a playlist is live, without having to look.
     *
     * An M3U file holds channels and nothing else — there is no per-entry type to read and no
     * exception to make — so the answer is the same for every url this source ever hands out. It is
     * stated here so the app stops inferring it from a stream that happens to report no duration.
     */
    override fun formOf(entryUrl: String): EntryForm = EntryForm.Live

    /**
     * The playlist's own groups, as a picker.
     *
     * `group-title` is the only categorisation an M3U file carries, and it is the one a provider
     * actually curates — Sport, News, a country, a package. Without this, a ten-thousand-channel
     * playlist offered a grid and a search box and no way to say *show me the sports channels*,
     * which is how anybody who has used an IPTV app expects to start.
     *
     * Empty on the first browse after a restart. The groups come out of the parsed file and this
     * call cannot suspend to fetch one, so the picker appears once the playlist has been read —
     * see [M3uPlaylistStore.cachedGroups].
     */
    override fun getFilterList(): AnimeFilterList {
        val groups = store.cachedGroups(playlist.url)
        if (groups.isEmpty()) return AnimeFilterList()
        return AnimeFilterList(GroupFilter(groups))
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage = pageOf(channels(), page)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    /**
     * Searching a file rather than a service.
     *
     * Matched against the group as well as the name, because *Sport* is how people look for a
     * sports channel whose name is a broadcaster they have never heard of — and the group is the
     * only categorisation an M3U file carries.
     */
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val needle = query.trim()
        val group = filters.filterIsInstance<GroupFilter>().firstOrNull()?.selected()

        val matching = channels()
            .filter { group == null || it.group == group }
            .filter {
                needle.isEmpty() ||
                    it.name.contains(needle, ignoreCase = true) ||
                    // Still matched against the group even with the picker available: typing
                    // "sport" is faster than opening a sheet, and both should work.
                    it.group?.contains(needle, ignoreCase = true) == true
            }
        return pageOf(matching, page)
    }

    /**
     * A channel's own details, which are whatever the playlist said about it.
     *
     * Nothing is fetched. There is no per-channel document in an M3U file — the one line that
     * describes a channel is the same line that was already read to list it — so this reads back
     * out of the list rather than pretending to look anything up.
     */
    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val channel = channelFor(anime.url) ?: return anime
        return toSAnime(channel).apply { initialized = true }
    }

    /** One row, because a channel is one thing and it is on now. */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val channel = channelFor(anime.url) ?: return emptyList()
        return listOf(
            SEpisode.create().apply {
                url = channel.id
                name = LIVE_ITEM_NAME
                episode_number = 1f
                date_upload = 0L
            },
        )
    }

    /**
     * The stream, as the playlist gave it.
     *
     * One hoster with one video: a playlist offers exactly one address per channel, and a quality
     * picker over a list of one is a dialog that exists to be dismissed. Where a provider lists
     * the same channel at several qualities they are separate entries with separate names, which
     * is the provider's decision and is left as it made it.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val channel = channelFor(episode.url) ?: return emptyList()
        return listOf(
            Hoster(
                hosterUrl = channel.url,
                hosterName = name,
                videoList = listOf(
                    Video(
                        videoUrl = channel.url,
                        videoTitle = channel.name,
                        // Whatever the playlist said this stream needs. The player sends a video's
                        // own headers in preference to the source's, and the downloader passes
                        // them to ffmpeg, so stating them here is the whole of what is required —
                        // and without them a provider that checks the User-Agent answers 403 to
                        // everything, which on screen is a channel that will not play with no
                        // reason given.
                        headers = channel.headers.takeIf { it.isNotEmpty() }?.let { headers ->
                            Headers.Builder().apply {
                                headers.forEach { (name, value) -> add(name, value) }
                            }.build()
                        },
                    ),
                ),
            ),
        )
    }

    /**
     * The playlist, for both — because a channel has no page.
     *
     * *Open in browser* exists to show the thing's own page on its own site. A channel's only
     * address is its video stream, and handing that to a browser downloads a file or plays
     * nothing. The playlist is the nearest thing to a page this source has.
     */
    override fun getAnimeUrl(anime: SAnime): String = playlist.url

    override fun getEpisodeUrl(episode: SEpisode): String = playlist.url

    private suspend fun channels(): List<M3uChannel> = store.channels(playlist.url)

    private suspend fun channelFor(id: String): M3uChannel? = channels().firstOrNull { it.id == id }

    private fun toSAnime(channel: M3uChannel): SAnime = SAnime.create().apply {
        url = channel.id
        title = channel.name
        thumbnail_url = channel.logo
        genre = channel.group
        fetch_type = FetchType.Episodes
        // The one row a channel has is the same row forever. Re-asking on every library update
        // would refetch the whole playlist once per channel, which for a ten-thousand-channel file
        // is ten thousand downloads of that file.
        update_strategy = AnimeUpdateStrategy.ONLY_FETCH_ONCE
    }

    /**
     * One page of the list.
     *
     * Sliced rather than sent whole. A grid handed ten thousand covers at once loads ten thousand
     * logos at once, and the difference between that and a page is the difference between a screen
     * that appears and a screen that arrives eventually.
     */
    private fun pageOf(channels: List<M3uChannel>, page: Int): AnimesPage {
        val from = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        if (from >= channels.size) return AnimesPage(emptyList(), false)
        val slice = channels.subList(from, minOf(from + PAGE_SIZE, channels.size))
        return AnimesPage(slice.map(::toSAnime), from + slice.size < channels.size)
    }

    private class GroupFilter(groups: List<String>) :
        AnimeFilter.Select<String>(FILTER_GROUP, (listOf(ANY_GROUP) + groups).toTypedArray()) {
        fun selected(): String? = values.getOrNull(state)?.takeIf { it != ANY_GROUP }
    }

    companion object {
        /**
         * A stable id for a playlist address.
         *
         * Deliberately a different prefix from the Stremio one, so a playlist and an addon at the
         * same address — which happens, since some providers publish both — are two sources rather
         * than one that overwrites the other.
         */
        fun idFor(playlistUrl: String): Long {
            val key = "m3u/${playlistUrl.trim().trimEnd('/').lowercase()}"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                .reduce(Long::or) and Long.MAX_VALUE
        }

        private const val MULTI_LANG = "all"
        private const val PAGE_SIZE = 60
        private const val LIVE_ITEM_NAME = "Live"
        private const val FILTER_GROUP = "Group"
        private const val ANY_GROUP = "All"
    }
}
