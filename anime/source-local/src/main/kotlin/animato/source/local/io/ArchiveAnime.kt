package animato.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

/**
 * Video containers the local anime source can read.
 *
 * Aniyomi put this beside Mihon's `ArchiveManga` in Mihon's own file. It is anime-only, so it
 * belongs here instead and Mihon's copy stays as upstream wrote it.
 */
object ArchiveAnime {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv", "torrent")

    fun isSupported(file: UniFile): Boolean = file.extension in SUPPORTED_ARCHIVE_TYPES
}
