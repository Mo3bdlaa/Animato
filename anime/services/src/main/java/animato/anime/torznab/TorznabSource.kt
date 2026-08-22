package animato.anime.torznab

import animato.anime.content.BrowsableByCategory
import animato.anime.content.EntryForm
import animato.anime.content.KnowsEntryForm
import animato.anime.content.SourceCategory
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * One Torznab indexer, seen as one source.
 *
 * ## What an entry is here, and why that is the whole design
 *
 * An indexer does not know what a series is. It knows about *releases* — files somebody uploaded,
 * with a name that happens to describe what is inside. Two rips of one episode are two releases,
 * and a whole season is often one.
 *
 * So a row in this catalogue is a release, and every one of them is [EntryForm.Single]: one thing,
 * complete, with one row to play. That is not a limitation being worked around, it is what the data
 * is — and pretending otherwise, by grouping releases into invented "series" from their filenames,
 * would be guessing in a way that is wrong often and silently.
 *
 * ## Why there is no metadata
 *
 * A release has a name, a size, a seeder count and a date, and nothing else — no cover, no
 * description, no cast. The catalogue is therefore text, which is what an indexer is. Somebody who
 * wants posters wants a catalogue addon in front of a stream provider, which the Stremio side
 * already does; this is the other thing, and it is the one that finds a release nothing else has.
 *
 * ## Where the magnet comes from later
 *
 * There is no per-release endpoint in Torznab. An indexer answers searches and cannot be asked
 * about one result, so nothing can look a release up again once it has scrolled out of a response —
 * the magnet has to be kept, or the row is dead.
 *
 * It is kept twice. In memory for the life of the process, which covers browsing; and in the
 * entry's own `memo`, which is the field the source API provides for a source to store its own
 * data and which survives into the database and back out.
 *
 * The first version of this kept only the first, reasoning that a magnet points at a swarm that may
 * be gone tomorrow and that a stale one is a row which fails on tap. That got the comparison
 * backwards: the alternative was not a working row, it was a row that fails on tap *always* rather
 * than sometimes. Anything added to the library from an indexer simply stopped working at the next
 * restart. A magnet that no longer has seeders fails no worse than one that is not there, and one
 * that still does is the whole point.
 */
class TorznabSource(
    val indexer: TorznabIndexer,
) : AnimeHttpSource(), KnowsEntryForm, BrowsableByCategory {

    override val baseUrl: String = indexer.url

    override val name: String = indexer.name

    /** An index carries whatever was uploaded to it, which is every language at once. */
    override val lang: String = MULTI_LANG

    override val id: Long by lazy { idFor(indexer.url) }

    /**
     * There is one order and it is *recent*, so a second shelf would be the same shelf twice.
     *
     * Which is the opposite of a shortcoming: newest-first is the only ordering a torrent index
     * has, and it is exactly the one somebody opening it wants.
     */
    override val supportsLatest: Boolean = false

    /**
     * Every release is one complete thing.
     *
     * Including a season pack, which is one file set and one row to press — the torrent server
     * picks the file. Calling that a serial would promise an episode list this source has no way
     * to produce.
     */
    override fun formOf(entryUrl: String): EntryForm = EntryForm.Single

    /**
     * What the indexer says it carries.
     *
     * Fetched rather than hardcoded to the Newznab numbering. Jackett and Prowlarr both remap
     * categories per indexer, so 5070 is anime on one and something else on the next — the only
     * true answer is the one the endpoint gives.
     */
    override suspend fun categories(): List<SourceCategory> {
        val xml = fetch(TorznabUrls.caps(indexer.url, indexer.apiKey))
        return TorznabFeed.parseCaps(xml).categories.map { SourceCategory(id = it.id, label = it.name) }
    }

    override suspend fun browseCategory(categoryId: String, page: Int): AnimesPage =
        page(query = null, categories = categoryId, page = page)

    override suspend fun getPopularAnime(page: Int): AnimesPage = page(query = null, categories = null, page = page)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = getPopularAnime(page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        page(query = query.trim().takeIf { it.isNotEmpty() }, categories = null, page = page)

    /**
     * Nothing to fetch, because there is nothing more to know.
     *
     * The row already carries everything the indexer said about this release. A details call would
     * be a request that could only return what is already on screen.
     */
    override suspend fun getAnimeDetails(anime: SAnime): SAnime =
        held(anime.url)?.let(::toSAnime)?.apply { initialized = true } ?: anime

    /**
     * One row, because a release is one thing.
     *
     * Built from whatever is in hand: the search result if this process listed it, and otherwise
     * the entry's own memo — which is the case for anything opened out of the library after a
     * restart, and the reason the memo is written at all.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val release = held(anime.url)
        val magnet = release?.magnet ?: TorznabFeed.magnetIn(anime.memo) ?: return emptyList()
        return listOf(
            SEpisode.create().apply {
                url = anime.url
                name = release?.title ?: anime.title
                episode_number = 1f
                date_upload = release?.publishedAt ?: TorznabFeed.publishedIn(anime.memo)
                // Carried onto the episode as well as the entry, because the episode is what the
                // player is handed and it must not have to reach back for the row it came from.
                memo = release?.let(TorznabFeed::memoOf) ?: anime.memo
            },
        )
    }

    /**
     * The magnet, spelled the way the player already reads torrents.
     *
     * No file index appended, unlike the Stremio side: Torznab says nothing about what is inside a
     * torrent, so there is no index to state and the torrent server picks the largest playable file
     * — which for a single release is the right file and for a season pack is the only guess
     * available to anybody.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val release = releases[episode.url]
        val magnet = release?.magnet ?: TorznabFeed.magnetIn(episode.memo) ?: return emptyList()
        val title = release?.title ?: episode.name
        return listOf(
            Hoster(
                hosterUrl = magnet,
                hosterName = indexer.name,
                videoList = listOf(Video(videoUrl = magnet, videoTitle = title)),
            ),
        )
    }

    /**
     * The indexer, for both — because a release has no page.
     *
     * An indexer proxies somebody else's site and the link it returns is the download, not a page
     * about it. Handing that to a browser starts a download or plays nothing.
     */
    override fun getAnimeUrl(anime: SAnime): String = indexer.url

    override fun getEpisodeUrl(episode: SEpisode): String = indexer.url

    private suspend fun page(query: String?, categories: String?, page: Int): AnimesPage {
        val offset = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        val xml = fetch(
            TorznabUrls.search(
                base = indexer.url,
                apiKey = indexer.apiKey,
                query = query,
                categories = categories,
                offset = offset,
                limit = PAGE_SIZE,
            ),
        )
        val found = TorznabFeed.parseSearch(xml)
        found.forEach { releases[it.id] = it }
        return AnimesPage(
            animes = found.map(::toSAnime),
            // Torznab has no "there is more" flag, so a full page means keep going — and erring
            // towards stopping, because an indexer that ignores `offset` would otherwise hand back
            // the same releases forever to a grid that keeps asking.
            hasNextPage = found.size >= PAGE_SIZE,
        )
    }

    private fun toSAnime(release: TorznabRelease): SAnime = SAnime.create().apply {
        url = release.id
        memo = TorznabFeed.memoOf(release)
        title = release.title
        // What there is to say, which is what somebody chooses a release by. Seeders first: a
        // release with none is one that will never start, whatever else is true of it.
        description = listOfNotNull(
            "${release.seeders} seeders",
            release.sizeBytes.takeIf { it > 0 }?.let(::humanSize),
            release.category,
        ).joinToString(" · ")
        genre = release.category
        fetch_type = FetchType.Episodes
        // A release never changes. It is a file that was uploaded once, so asking again on every
        // library cycle is a request per row that can only ever repeat itself.
        update_strategy = AnimeUpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun held(entryUrl: String): TorznabRelease? = releases[entryUrl]

    /**
     * A response, or the indexer's complaint said out loud.
     *
     * The complaint arrives with a 200, so `awaitSuccess` is satisfied and the parse below simply
     * finds nothing — see [TorznabFeed.errorIn]. Thrown rather than returned empty because the
     * browse screen renders a thrown message and shows an empty grid for an empty list.
     */
    private suspend fun fetch(url: String): String {
        val body = client.newCall(GET(url, headers)).awaitSuccess().body.string()
        TorznabFeed.errorIn(body)?.let { throw IOException("${indexer.name}: $it") }
        return body
    }

    private fun humanSize(bytes: Long): String {
        if (bytes < UNIT) return "$bytes B"
        val exponent = (Math.log(bytes.toDouble()) / Math.log(UNIT.toDouble())).toInt()
        val unit = "KMGTPE"[exponent - 1]
        return "%.1f %sB".format(bytes / Math.pow(UNIT.toDouble(), exponent.toDouble()), unit)
    }

    companion object {
        /**
         * Every release this source has listed in this process.
         *
         * Static, and shared across instances of the same indexer, because the source object is
         * rebuilt whenever the indexer list changes — adding a second indexer would otherwise empty
         * the first one's results out from under a title page somebody had open.
         *
         * Never persisted. A magnet is a pointer to a swarm that may be gone tomorrow, and a stale
         * one stored in a library row is a row that fails on tap with nothing to say about why.
         * Anything in the library from here is re-found by searching for it.
         */
        private val releases = ConcurrentHashMap<String, TorznabRelease>()

        fun idFor(indexerUrl: String): Long {
            val key = "torznab/${indexerUrl.trim().trimEnd('/').lowercase()}"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                .reduce(Long::or) and Long.MAX_VALUE
        }

        private const val MULTI_LANG = "all"
        private const val PAGE_SIZE = 50
        private const val UNIT = 1024
    }
}
