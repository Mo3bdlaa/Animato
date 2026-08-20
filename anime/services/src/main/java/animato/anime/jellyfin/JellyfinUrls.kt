package animato.anime.jellyfin

import java.net.URLEncoder

/**
 * Where to send each question to a Jellyfin or Emby server.
 *
 * ## The one thing that is not obvious
 *
 * Everything a *user* can see is under `/Users/{userId}/…`, and everything about a *show* is under
 * a top-level path. `/Users/{id}/Items` is the catalogue, `/Shows/{id}/Episodes` is the episode
 * list, and the second one takes the user as a query parameter instead. Getting that backwards
 * returns an empty list rather than an error, which is a server that looks empty rather than one
 * that looks misconfigured.
 *
 * ## Why Emby needs no separate builder
 *
 * Emby is Jellyfin's ancestor and these paths are the part that did not diverge. Both answer the
 * same routes with the same shapes; where they differ is in the extras this app does not ask for.
 * The base URL is whatever the person pasted, so an Emby install served under `/emby` works by
 * having that in the address rather than by a flag in here.
 */
object JellyfinUrls {

    /**
     * Reduce whatever was pasted to a base URL.
     *
     * People paste the address bar of their own server, which means a scheme may be missing, a
     * trailing slash is likely, and `/web/index.html` — the path the Jellyfin UI actually sits at —
     * is common. What must survive is any other path: an install behind a reverse proxy at
     * `example.test/media` is a real and ordinary setup, and trimming to the host would point every
     * request at nothing.
     */
    fun normalizeBase(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.contains("://")) url = "https://$url"
        url = url.trimEnd('/')
        // The UI's own path, in whichever of its forms was copied. Everything below is the API,
        // which lives at the root beside it rather than under it.
        WEB_SUFFIXES.forEach { suffix ->
            if (url.endsWith(suffix, ignoreCase = true)) {
                url = url.dropLast(suffix.length)
            }
        }
        return url.trimEnd('/')
    }

    fun authenticate(base: String): String = "${normalizeBase(base)}/Users/AuthenticateByName"

    /** The user's libraries. What a person calls Movies, Shows, Anime on their own server. */
    fun views(base: String, userId: String): String =
        "${normalizeBase(base)}/Users/${userId.escaped()}/Views"

    /**
     * A page of a library, or of the whole server when [parentId] is null.
     *
     * `Recursive` because a library is a tree and the catalogue is a flat grid: without it, a
     * library organised into folders answers with the folders.
     */
    fun items(
        base: String,
        userId: String,
        parentId: String? = null,
        search: String? = null,
        startIndex: Int = 0,
        limit: Int = 0,
        sortBy: String = SORT_NAME,
        descending: Boolean = false,
    ): String {
        val query = buildList {
            add("Recursive" to "true")
            add("IncludeItemTypes" to "Movie,Series")
            add("Fields" to ITEM_FIELDS)
            add("SortBy" to sortBy)
            add("SortOrder" to if (descending) "Descending" else "Ascending")
            add("StartIndex" to startIndex.toString())
            if (limit > 0) add("Limit" to limit.toString())
            parentId?.takeIf { it.isNotBlank() }?.let { add("ParentId" to it) }
            search?.takeIf { it.isNotBlank() }?.let { add("SearchTerm" to it) }
        }
        return "${normalizeBase(base)}/Users/${userId.escaped()}/Items${query.asQuery()}"
    }

    /** One item, with the fields a title page needs. */
    fun item(base: String, userId: String, itemId: String): String =
        "${normalizeBase(base)}/Users/${userId.escaped()}/Items/${itemId.escaped()}" +
            listOf("Fields" to ITEM_FIELDS).asQuery()

    /**
     * Every episode of a series, across every season, in order.
     *
     * All at once rather than season by season. The app's episode list is one list, and a series
     * with five seasons would otherwise be five requests to draw one screen.
     */
    fun episodes(base: String, userId: String, seriesId: String): String {
        val query = listOf(
            "userId" to userId,
            "Fields" to EPISODE_FIELDS,
        )
        return "${normalizeBase(base)}/Shows/${seriesId.escaped()}/Episodes${query.asQuery()}"
    }

    /**
     * A cover, at a size worth downloading.
     *
     * Capped by height rather than fetched whole: a server stores the poster its scraper found,
     * which is routinely three thousand pixels tall, and a grid of those is a library that takes a
     * minute to draw and fills the image cache with one screen.
     *
     * The tag is the point of the query string. It changes when the image does, so a replaced
     * cover appears rather than waiting for a cache to expire.
     */
    fun image(base: String, itemId: String, tag: String?, kind: String = IMAGE_PRIMARY, maxHeight: Int = 720): String {
        val query = buildList {
            add("maxHeight" to maxHeight.toString())
            tag?.takeIf { it.isNotBlank() }?.let { add("tag" to it) }
        }
        return "${normalizeBase(base)}/Items/${itemId.escaped()}/Images/$kind${query.asQuery()}"
    }

    /**
     * The video itself, as a URL the player can be handed directly.
     *
     * `static=true` asks for the original file rather than a transcode. That is the right default
     * for this app: mpv plays essentially everything a server would otherwise spend CPU converting,
     * and a transcode is a server working hard to produce a worse copy of a file that would have
     * played. A codec mpv genuinely cannot handle is rare enough to be worth failing on rather than
     * pre-emptively degrading every stream.
     *
     * The token goes in the query rather than a header because this URL leaves the app: it is
     * handed to mpv, to the downloader, and to a cast target, and only the first of those would
     * carry headers we attached.
     */
    fun stream(base: String, itemId: String, token: String): String {
        val query = listOf(
            "static" to "true",
            "api_key" to token,
        )
        return "${normalizeBase(base)}/Videos/${itemId.escaped()}/stream${query.asQuery()}"
    }

    /** The item's own page in the server's web UI, for *open in browser*. */
    fun webPage(base: String, itemId: String, serverId: String): String {
        val query = listOf("id" to itemId, "serverId" to serverId)
        return "${normalizeBase(base)}/web/index.html#!/details${query.asQuery()}"
    }

    private fun List<Pair<String, String>>.asQuery(): String =
        if (isEmpty()) "" else "?" + joinToString("&") { (k, v) -> "$k=${v.escaped()}" }

    private fun String.escaped(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    const val IMAGE_PRIMARY = "Primary"
    const val IMAGE_BACKDROP = "Backdrop"
    const val SORT_NAME = "SortName"
    const val SORT_DATE_CREATED = "DateCreated"

    /**
     * The extra fields a catalogue row and a title page need.
     *
     * Asked for by name because Jellyfin omits all of them by default — a plain `/Items` answers
     * with ids and names, and a grid built from that has no covers and no descriptions.
     */
    private const val ITEM_FIELDS = "Overview,Genres,Studios,People,ProductionYear,PremiereDate,Status"
    private const val EPISODE_FIELDS = "Overview,PremiereDate"

    private val WEB_SUFFIXES = listOf("/web/index.html", "/web/", "/web")
}
