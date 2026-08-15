package animato.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * `animateItem` with the fade specs disabled.
 *
 * Fast-scrolling a lazy list makes items appear and disappear faster than the default fade can
 * finish, so the fade reads as flicker. See https://issuetracker.google.com/352584409.
 *
 * Aniyomi wrote this with an anonymous `context(LazyItemScope)`. Kotlin now requires context
 * parameters to be named, so the scope is bound and used explicitly.
 */
context(scope: LazyItemScope)
fun Modifier.animateItemFastScroll(): Modifier = with(scope) {
    this@animateItemFastScroll.animateItem(fadeInSpec = null, fadeOutSpec = null)
}
