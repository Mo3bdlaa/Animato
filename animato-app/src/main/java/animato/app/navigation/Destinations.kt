package animato.app.navigation

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import animato.app.discover.DiscoverContent
import animato.app.extension.ExtensionsContent
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

/**
 * Sources, in the slot the download queue used to hold.
 *
 * The trade, weighed on a device: *"Manage next to Your sources is far too small for how important
 * sources are — what if it took Downloads' place?"* A bar slot earns its keep when it is somewhere
 * you go without a reason having appeared. A download queue is the opposite: empty unless something
 * is being fetched, and loudly present in the notification shade when it is. Sources is a place to
 * look around in — which of your sites has something new — and it was three taps away.
 *
 * The queue did not lose its home, it moved to one that only appears when it has something to say:
 * an icon with a live count in the Updates top bar. See `UpdatesContent`.
 */
data object AnimatoSourcesTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = stringResource(MR.strings.label_sources),
            icon = rememberVectorPainter(Icons.Outlined.Extension),
        )

    @Composable
    override fun Content() = ExtensionsContent()
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
