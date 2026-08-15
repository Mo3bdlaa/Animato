package animato.anime.player

import android.content.Context
import androidx.core.net.toUri
import animato.anime.player.getFontsDirectory
import animato.anime.player.getMPVConfigDirectory
import animato.anime.player.getScriptOptsDirectory
import animato.anime.player.getScriptsDirectory
import animato.anime.player.getShadersDirectory
import com.hippo.unifile.UniFile
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import uy.kohesive.injekt.injectLazy

/**
 * The directories mpv reads its configuration, fonts, scripts and shaders from.
 *
 * Aniyomi added these methods to Mihon's own [StorageManager]. They are extensions here instead,
 * and they resolve the base directory from [StoragePreferences] — the same preference
 * [StorageManager] itself reads — rather than reaching for its private field or walking up from one
 * of its directories. Reading the preference each time also means a base directory the user changed
 * takes effect immediately.
 */
private val context: Context by injectLazy()
private val storagePreferences: StoragePreferences by injectLazy()

private fun baseDirectory(): UniFile? =
    UniFile.fromUri(context, storagePreferences.baseStorageDirectory.get().toUri())
        ?.takeIf { it.exists() }

fun StorageManager.getMPVConfigDirectory(): UniFile? = baseDirectory()?.createDirectory(MPV_CONFIG_PATH)

fun StorageManager.getFontsDirectory(): UniFile? = getMPVConfigDirectory()?.createDirectory(FONTS_PATH)

fun StorageManager.getScriptsDirectory(): UniFile? = getMPVConfigDirectory()?.createDirectory(SCRIPTS_PATH)

fun StorageManager.getScriptOptsDirectory(): UniFile? =
    getMPVConfigDirectory()?.createDirectory(SCRIPT_OPTS_PATH)

fun StorageManager.getShadersDirectory(): UniFile? = getMPVConfigDirectory()?.createDirectory(SHADERS_PATH)

const val MPV_CONFIG_PATH = "mpv"
const val FONTS_PATH = "fonts"
const val SCRIPTS_PATH = "scripts"
const val SCRIPT_OPTS_PATH = "script-opts"
const val SHADERS_PATH = "shaders"
