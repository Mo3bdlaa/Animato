package animato.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

/**
 * Directory holding local anime files.
 *
 * Aniyomi added this as a method on Mihon's [StorageManager]. Mihon keeps `baseDir` private, so the
 * directory is reached through the local manga source's parent instead — the same folder, without
 * opening their file. The name is unchanged, so existing libraries are found where they already are.
 */
fun StorageManager.getLocalAnimeSourceDirectory(): UniFile? =
    getLocalSourceDirectory()?.parentFile?.createDirectory(LOCAL_ANIME_SOURCE_PATH)

private const val LOCAL_ANIME_SOURCE_PATH = "localanime"
