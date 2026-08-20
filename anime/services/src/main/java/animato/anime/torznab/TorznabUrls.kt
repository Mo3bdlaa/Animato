package animato.anime.torznab

import java.net.URLEncoder

/**
 * Where to send each question to a Torznab indexer.
 *
 * The whole API is one path with a `t=` parameter deciding what is being asked:
 *
 * ```
 * /api?t=caps&apikey=…
 * /api?t=search&q=…&cat=…&offset=…&limit=…&apikey=…
 * ```
 *
 * Jackett and Prowlarr both serve this, at addresses that look nothing alike —
 * `…/api/v2.0/indexers/nyaa/results/torznab/api` and `…/1/api` respectively. So nothing here tries
 * to construct the endpoint: what somebody pastes is the endpoint, because it is the same string
 * they already pasted into Sonarr.
 */
object TorznabUrls {

    /**
     * Reduce what was pasted to something `?t=…` can be appended to.
     *
     * Both dashboards hand out the URL with a query string already on it — Jackett's copy button
     * includes `?apikey=`. Keeping that would produce two `apikey` parameters, one of them possibly
     * stale, and an indexer that answers *unauthorised* to a key the person is looking at while it
     * fails.
     */
    fun normalizeBase(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (!url.contains("://")) url = "https://$url"
        url = url.substringBefore('?').trimEnd('/')
        // Both dashboards' copy buttons end at `/api`; somebody who trimmed it by hand should not
        // get a different endpoint from somebody who did not.
        if (!url.endsWith(API_PATH, ignoreCase = true)) url += API_PATH
        return url
    }

    fun caps(base: String, apiKey: String): String =
        base + query("t" to "caps", "apikey" to apiKey)

    /**
     * A search, or the indexer's own idea of what is new.
     *
     * A blank query is deliberate and is what fills the first screen: Torznab answers `t=search`
     * with no `q` by listing the most recent releases, which is the only ordering an indexer has
     * and the one thing a torrent index is genuinely good at.
     */
    fun search(
        base: String,
        apiKey: String,
        query: String? = null,
        categories: String? = null,
        offset: Int = 0,
        limit: Int = 0,
    ): String = base + query(
        *buildList {
            add("t" to "search")
            add("apikey" to apiKey)
            query?.takeIf { it.isNotBlank() }?.let { add("q" to it) }
            categories?.takeIf { it.isNotBlank() }?.let { add("cat" to it) }
            if (offset > 0) add("offset" to offset.toString())
            if (limit > 0) add("limit" to limit.toString())
        }.toTypedArray(),
    )

    private fun query(vararg params: Pair<String, String>): String =
        "?" + params.joinToString("&") { (k, v) -> "$k=${v.escaped()}" }

    private fun String.escaped(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private const val API_PATH = "/api"
}
