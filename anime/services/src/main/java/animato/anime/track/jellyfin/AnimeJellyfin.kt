package animato.anime.track.jellyfin

import animato.anime.services.R
import animato.anime.track.AnimeOnlyTracker
import animato.anime.track.AnimeTrackerIds
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okhttp3.Dns
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * A Jellyfin server, tracked as though it were a tracking site.
 *
 * The odd one out, and deliberately: Jellyfin is where the episodes are *played from*, not a list
 * kept somewhere else. So there is nothing to search and nothing to match by hand — an anime coming
 * from the Jellyfin extension already knows its own address on the server, and that address is the
 * link. That is what [EnhancedAnimeTracker] means, and why [searchAnime] refuses rather than
 * returning nothing: being asked to search is a caller's mistake, not an empty result.
 *
 * It follows that there is no sign-in either. The server, the account and the key were configured
 * once in the extension, and [JellyfinInterceptor] reads them from there.
 *
 * There is no score: a server has no opinion about what is good.
 */
class AnimeJellyfin : AnimeOnlyTracker(AnimeTrackerIds.JELLYFIN, "Jellyfin"), EnhancedAnimeTracker {

    companion object {
        const val UNSEEN = 1L
        const val WATCHING = 2L
        const val COMPLETED = 3L

        private const val NO_REWATCHING_STATUS = -1L

        /**
         * The extension this tracker belongs to, by class name. Anything else is not a Jellyfin
         * server and cannot be tracked here.
         */
        private const val JELLYFIN_EXTENSION = "eu.kanade.tachiyomi.animeextension.all.jellyfin.Jellyfin"
    }

    override val client by lazy {
        networkService.client.newBuilder()
            .addInterceptor(JellyfinInterceptor())
            // A Jellyfin server is usually a private address, which DNS over HTTPS cannot resolve —
            // and Mihon's shared client uses it. The system resolver is the one that knows.
            .dns(Dns.SYSTEM)
            .build()
    }

    private val api by lazy { JellyfinApi(id, client) }

    override fun getLogo() = R.drawable.ic_tracker_jellyfin

    override fun getStatusListAnime(): List<Long> = listOf(UNSEEN, WATCHING, COMPLETED)

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        UNSEEN -> AYMR.strings.unseen
        WATCHING -> AYMR.strings.watching
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = NO_REWATCHING_STATUS

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainAnimeTrack): String = ""

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack =
        api.updateProgress(track)

    /**
     * Nothing to bind. The link is the anime's own url, and it was there before this was called.
     */
    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack = track

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> =
        throw UnsupportedOperationException("Jellyfin matches by url; there is nothing to search")

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    /**
     * Signing in is the extension's job, so both of these only record that it happened — the
     * credentials are a marker, and `isLoggedIn` is what reads them.
     */
    override suspend fun login(username: String, password: String) = loginNoop()

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf(JELLYFIN_EXTENSION)

    override suspend fun match(anime: Anime): AnimeTrackSearch? = try {
        api.getTrackSearch(anime.url)
    } catch (e: Exception) {
        null
    }

    override fun isTrackFrom(track: DomainAnimeTrack, anime: Anime, source: AnimeSource?): Boolean =
        track.remoteUrl == anime.url && source?.let { accept(it) } == true

    override fun migrateTrack(
        track: DomainAnimeTrack,
        anime: Anime,
        newSource: AnimeSource,
    ): DomainAnimeTrack? = if (accept(newSource)) track.copy(remoteUrl = anime.url) else null
}
