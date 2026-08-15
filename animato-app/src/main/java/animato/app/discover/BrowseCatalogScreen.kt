package animato.app.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import animato.app.navigation.AnimeBrowseScreen
import animato.app.navigation.contentType
import animato.domain.content.ContentType
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.BrowseTab

/**
 * The source and extension lists, as a screen rather than a destination.
 *
 * In Mihon these are Browse, the way into everything. In Animato they are what Discover is
 * configured with — which sources it asks, and which extensions provide them — so they sit one tap
 * behind it instead of in front of it.
 *
 * The screens themselves are unchanged: this hosts the same three tabs each library already had.
 */
class BrowseCatalogScreen(
    private val toExtensions: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        LaunchedEffect(toExtensions) {
            if (toExtensions) BrowseTab.showExtension()
        }
        when (contentType()) {
            ContentType.MANGA -> BrowseTab.Content()
            ContentType.ANIME -> AnimeBrowseScreen()
        }
    }
}
