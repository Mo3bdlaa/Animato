package tachiyomi.source.local.io.anime

import animato.anime.di.AnimatoScope
import animato.source.local.io.getLocalAnimeSourceDirectory
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.storage.service.StorageManager

@SingleIn(AnimatoScope::class)
@Inject
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
