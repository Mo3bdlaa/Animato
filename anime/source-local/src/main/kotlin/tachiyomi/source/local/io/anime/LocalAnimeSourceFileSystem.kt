package tachiyomi.source.local.io.anime

import animato.source.local.io.getLocalAnimeSourceDirectory
import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager

class LocalAnimeSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalAnimeSourceDirectory()
    }

    fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    fun getAnimeDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    fun getFilesInAnimeDirectory(name: String): List<UniFile> {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
            ?.listFiles().orEmpty().toList()
    }
}
