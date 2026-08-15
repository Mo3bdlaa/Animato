package animato.anime.util

import com.hippo.unifile.UniFile

/**
 * Returns the size of a file or directory.
 */
fun UniFile.size(): Long {
    var totalSize = 0L
    if (isDirectory) {
        listFiles()?.forEach { file ->
            totalSize += if (file.isDirectory) {
                file.size()
            } else {
                val length = file.length()
                if (length > 0) length else 0
            }
        }
    } else {
        totalSize = length()
    }
    return totalSize
}
