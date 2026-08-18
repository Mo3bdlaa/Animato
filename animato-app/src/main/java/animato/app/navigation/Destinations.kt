package animato.app.navigation

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import animato.app.discover.DiscoverContent
import animato.app.downloads.DownloadsContent
import animato.app.library.UnifiedLibraryContent
import animato.app.updates.UpdatesContent
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.ui.updates.UpdatesViewModel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The four destinations that show one library at a time.
 *
 * Each renders the manga screen or the anime screen depending on the lens, and re-selecting the
 * destination flips it — the same gesture Mihon already uses for a tab's secondary action, and the
 * only one available without stealing space from screens whose toolbars are not ours.
 *
 * These are wrappers, not screens: the work is still done by the screens each side already has.
 * They exist because Animato's bar has one Library rather than two, and something has to decide
 * which Library that is. When the unified screens land they replace the delegation, not the tab.
 */

data object AnimatoLibraryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_library),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    // Re-selecting the tab no longer cycles the lens. The lens is a labelled control in the top
    // bar now, and a gesture that silently changes what you are looking at is the thing that
    // control exists to replace.

    /**
     * One library screen, under every lens.
     *
     * A narrowed lens used to hand the whole destination to Mihon's library or the ported anime
     * one, and that is how a device ended up looking at two different title pages: those screens
     * open *their* entry screens, so filtering to Anime silently swapped the entire visual
     * language of everything below it — *"we want those two to look like the one we made."*
     *
     * The unified grid never needed the delegation. It reads the lens itself: entries are filtered
     * by `lens.accepts`, categories hide when they have nothing to say under it, and a selection
     * survives a lens change only where it still means something. What the per-half screens still
     * hold that this one does not is reachable from the title page's overflow, which is where the
     * deep tools have lived since they stopped being the front door.
     */
    @Composable
    override fun Content() = UnifiedLibraryContent()
}

data object AnimatoDiscoverTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(AYMR.strings.label_discover),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() = DiscoverContent()
}

data object AnimatoUpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    // One feed for both halves. The per-library screens are gone from here rather than delegated
    // to: a day on which one chapter and one episode arrived was two screens holding one row each.
    @Composable
    override fun Content() = UpdatesContent()
}

data object AnimatoDownloadsTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = stringResource(MR.strings.label_download_queue),
            icon = rememberVectorPainter(Icons.Outlined.Download),
        )

    // One queue. Two of them cannot both be a claim about what the device is doing right now.
    @Composable
    override fun Content() = DownloadsContent()
}

data object AnimatoHomeTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 0u,
            title = stringResource(AYMR.strings.label_home),
            icon = rememberVectorPainter(Icons.Outlined.Home),
        )

    @Composable
    override fun Content() = HomeScreenContent()
}
