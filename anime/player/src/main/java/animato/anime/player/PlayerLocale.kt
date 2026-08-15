package animato.anime.player

import androidx.core.os.LocaleListCompat
import animato.anime.player.getSimpleLocaleDisplayName

/**
 * The device's language, in English, for labelling subtitle and audio tracks.
 *
 * Aniyomi added this to Mihon's `LocaleHelper` object. It is two lines and only the player's track
 * pickers ask for it.
 */
fun getSimpleLocaleDisplayName(): String = LocaleListCompat.getDefault()[0]!!.displayLanguage
