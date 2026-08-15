package animato.anime.player

import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.Preference
import tachiyomi.presentation.core.components.material.Padding

/**
 * A step between `small` (8dp) and `medium` (16dp), used throughout the player's controls where
 * 16dp crowds the video and 8dp reads as cramped.
 *
 * Aniyomi added it to Mihon's own `Padding` class. Mihon's still has five steps and this is a sixth
 * that only the player wants, so it is an extension of ours rather than an edit of theirs.
 */
val Padding.mediumSmall get() = 12.dp

/**
 * Clears a preference and returns the default it falls back to.
 *
 * Aniyomi added this to Mihon's `Preference.kt` in `core/common`. It is two lines and only the
 * player's "reset to default" controls use it.
 */
inline fun <reified T> Preference<T>.deleteAndGet(): T {
    delete()
    return get()
}
