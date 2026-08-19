package animato.app.entry

import animato.domain.content.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * What a person has corrected about an entry, kept apart from what its source says.
 *
 * Sources get things wrong. A title romanised three different ways across three sites, an author
 * field holding the scanlation group, a description that is the site's SEO paragraph. This is the
 * longest-standing open request in both upstreams — Mihon #5, Aniyomi #237 — and it has stayed open
 * partly because of where the edit has to live.
 *
 * ## Why this is not a column
 *
 * The obvious home is the entry's own row: set the title, done, and every screen shows it for free.
 * That fails on the refresh. `UpdateMangaFromRemote` overwrites title, author, artist, description
 * and genre with whatever the source just said, on every library update — and that interactor is
 * Mihon's, in Mihon's module, which this project does not edit. The anime half's equivalent is ours
 * and could be taught to spare an edited field, but a feature that survives a refresh on one half
 * and is silently undone on the other is worse than no feature.
 *
 * The `memo` JSON column on both tables looked like the answer for the same reason it exists — an
 * app-owned bag per entry, no migration. It is overwritten by the source's own memo on refresh, so
 * it has exactly the same problem.
 *
 * So the edit lives outside the entry, and the screens apply it on the way out. That is affordable
 * only because every surface that draws an entry in this app is now ours — Home, Library, Updates,
 * Discover, search and the entry page — which was not true a month ago.
 *
 * ## What is stored
 *
 * Only the fields that were actually changed. A null field means "whatever the source says", not
 * "blank", which is what makes clearing an override a real action rather than an edit to empty
 * string — and what stops an entry freezing at the values it happened to have on the day it was
 * edited.
 */
@Serializable
data class EntryOverride(
    val title: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = title == null && author == null && artist == null && description == null && genres == null
}

class EntryOverrides(
    preferenceStore: PreferenceStore = Injekt.get(),
) {
    private val preference = preferenceStore.getString(PREF_KEY, "")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val _overrides = MutableStateFlow(load())

    /**
     * Exposed as a flow so a screen recomposes the moment an edit is saved, including the screens
     * behind the one it was saved on.
     */
    val overrides: StateFlow<Map<String, EntryOverride>> = _overrides.asStateFlow()

    fun get(contentType: ContentType, entryId: Long): EntryOverride? =
        _overrides.value[key(contentType, entryId)]

    fun set(contentType: ContentType, entryId: Long, override: EntryOverride) {
        val next = _overrides.value.toMutableMap()
        // An override with nothing left in it is a deletion, not an empty record. Keeping it would
        // mean the entry never returns to following its source again.
        if (override.isEmpty) {
            next.remove(key(contentType, entryId))
        } else {
            next[key(contentType, entryId)] = override
        }
        persist(next)
    }

    fun clear(contentType: ContentType, entryId: Long) {
        persist(_overrides.value - key(contentType, entryId))
    }

    private fun persist(next: Map<String, EntryOverride>) {
        preference.set(json.encodeToString(next))
        _overrides.value = next
    }

    private fun load(): Map<String, EntryOverride> {
        val raw = preference.get()
        if (raw.isEmpty()) return emptyMap()
        return runCatching { json.decodeFromString<Map<String, EntryOverride>>(raw) }
            .getOrElse {
                logcat(LogPriority.ERROR, it) { "Stored entry overrides could not be read" }
                emptyMap()
            }
    }

    companion object {
        private const val PREF_KEY = "animato_entry_overrides"

        /**
         * The two halves number their entries independently, so an id alone is ambiguous: manga 12
         * and anime 12 are different works.
         */
        fun key(contentType: ContentType, entryId: Long): String = "${contentType.name}-$entryId"
    }
}

/** The title to draw, which is the corrected one wherever there is one. */
fun EntryOverride?.titleOr(sourceTitle: String): String = this?.title ?: sourceTitle
