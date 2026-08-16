package animato.app.updates

import androidx.compose.runtime.Immutable
import animato.domain.content.ContentType
import tachiyomi.domain.updates.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.updates.model.UpdatesWithRelations

/**
 * Something that arrived since you last looked, whichever library it came from.
 *
 * Both halves have their own `…UpdatesWithRelations` row, with the same seven facts under different
 * names — `chapterName` and `episodeName`, `read` and `seen`. This is the one shape the shared
 * screens draw, and it lives here rather than beside either screen because Home's *Latest updates*
 * and the Updates feed are the same object at two sizes.
 *
 * [itemId] and [sourceId] are carried so a row can be *acted on* and not merely listed: opening one
 * launches the reader or the player, and downloading one needs the source that provides it. A model
 * that only held what is drawn would have forced a second lookup on every tap.
 */
@Immutable
data class UpdateItem(
    val entryId: Long,
    val itemId: Long,
    val sourceId: Long,
    val contentType: ContentType,
    val title: String,
    val itemName: String,
    val fetchedAt: Long,
    /** Unread or unseen — the pill on the row, and what makes the feed news rather than a log. */
    val isNew: Boolean,
    val coverData: Any?,
)

fun UpdatesWithRelations.toUpdateItem() = UpdateItem(
    entryId = mangaId,
    itemId = chapterId,
    sourceId = sourceId,
    contentType = ContentType.MANGA,
    title = mangaTitle,
    itemName = chapterName,
    fetchedAt = dateFetch,
    isNew = !read,
    coverData = coverData,
)

fun AnimeUpdatesWithRelations.toUpdateItem() = UpdateItem(
    entryId = animeId,
    itemId = episodeId,
    sourceId = sourceId,
    contentType = ContentType.ANIME,
    title = animeTitle,
    itemName = episodeName,
    fetchedAt = dateFetch,
    isNew = !seen,
    coverData = coverData,
)
