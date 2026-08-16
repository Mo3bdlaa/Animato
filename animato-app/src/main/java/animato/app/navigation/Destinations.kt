package animato.app.navigation

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.discover.DiscoverContent
import animato.app.library.UnifiedLibraryContent
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.ui.navigation.AnimatoNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.library.LibraryViewModel
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
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

    @Composable
    override fun Content() {
        when (contentLens()) {
            // Both halves in one grid. Re-selecting the destination narrows to one, where the
            // per-library screens still hold everything the unified grid does not do yet.
            ContentFilter.ALL -> UnifiedLibraryContent()
            ContentFilter.MANGA -> {
                MirrorSelectionMode(viewModel<LibraryViewModel>().state.collectAsState().value.selectionMode)
                LibraryTab.Content()
            }
            ContentFilter.ANIME -> AnimeLibraryTab.Content()
        }
    }
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

    @Composable
    override fun Content() {
        when (contentTypeOrDefault()) {
            ContentType.MANGA -> {
                MirrorSelectionMode(viewModel<UpdatesViewModel>().state.collectAsState().value.selectionMode)
                UpdatesTab.Content()
            }
            ContentType.ANIME -> AnimeUpdatesScreen()
        }
    }
}

data object AnimatoDownloadsTab : Tab {

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 4u,
            title = stringResource(MR.strings.label_download_queue),
            icon = rememberVectorPainter(Icons.Outlined.Download),
        )

    @Composable
    override fun Content() {
        when (contentTypeOrDefault()) {
            ContentType.MANGA -> DownloadQueueScreen.Content()
            ContentType.ANIME -> AnimeDownloadsScreen()
        }
    }
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

/**
 * Hides the tab bar while a Mihon screen is in selection mode.
 *
 * Mihon's library and updates screens do this themselves, by sending to a channel private to
 * Mihon's own `HomeScreen`. That host is not the one mounted here and nothing outside its file can
 * receive from it, so the signal has to be read from the same place the screen reads it: its view
 * model. `viewModel()` resolves against the store this destination already owns, so this is the
 * same instance the screen inside is using, not a second one.
 */
@Composable
private fun MirrorSelectionMode(selectionMode: Boolean) {
    LaunchedEffect(selectionMode) {
        AnimatoNavigator.showBottomNav(!selectionMode)
    }
}
