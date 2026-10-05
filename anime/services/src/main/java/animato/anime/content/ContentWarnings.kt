package animato.anime.content

import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extension.model.ContentWarning

/*
 * The anime half's answers to Mihon's content-warning setting.
 *
 * Mihon replaced its single "show NSFW sources" switch with a set of allowed content warnings —
 * safe, mixed, NSFW — and an extension now declares which one it carries. The anime extensions
 * declare the same thing in the same metadata, so they are read the same way and held to the same
 * set: one setting for both halves, as it was before.
 */

/** Whether adult content is allowed at all — the question the Stremio list and the seeds ask. */
fun SourcePreferences.allowsNsfw(): Boolean = ContentWarning.NSFW in enabledContentWarnings.get()

/**
 * The warning an anime extension carries, from its manifest metadata.
 *
 * [declared] is `aniyomix.contentWarning` when present (0 safe, 1 mixed, 2 adult), and
 * [legacyNsfw] the older `tachiyomi.animeextension.nsfw` flag, which only ever meant adult.
 */
fun animeContentWarning(declared: Int?, legacyNsfw: Boolean): ContentWarning = when {
    declared != null -> when (declared) {
        1 -> ContentWarning.MIXED
        2 -> ContentWarning.NSFW
        else -> ContentWarning.SAFE
    }
    legacyNsfw -> ContentWarning.NSFW
    else -> ContentWarning.SAFE
}
