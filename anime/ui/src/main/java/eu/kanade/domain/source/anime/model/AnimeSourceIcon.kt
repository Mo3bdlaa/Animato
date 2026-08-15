package eu.kanade.domain.source.anime.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The installed extension's launcher icon, for a source that came from one.
 *
 * Null for a source with no extension behind it — the local source, or a stub left over from an
 * extension that has been uninstalled. Mihon has the same extension on its own `Source`.
 */
val AnimeSource.icon: ImageBitmap?
    get() {
        return Injekt.get<AnimeExtensionManager>().getAppIconForSource(id)
            ?.toBitmap()
            ?.asImageBitmap()
    }
