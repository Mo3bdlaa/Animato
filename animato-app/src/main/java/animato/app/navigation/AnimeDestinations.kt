package animato.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.download.anime.animeDownloadTab
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The anime halves of Discover, Updates and Downloads.
 *
 * Mihon's equivalents are `Tab` objects that assemble their own screen; the anime side produces
 * `TabContent` instead, which is what Aniyomi needed to put two content types under one tab. Here
 * that shape is an advantage — the destination composes the screen, so the anime screens arrive
 * without a tab of their own.
 */

@Composable
internal fun Screen.AnimeBrowseScreen() {
    val extensionsScreenModel = viewModel { AnimeExtensionsScreenModel() }
    val extensionsState by extensionsScreenModel.state.collectAsState()

    TabbedScreen(
        titleRes = AYMR.strings.label_discover,
        tabs = listOf(
            animeSourcesTab(),
            animeExtensionsTab(extensionsScreenModel),
            migrateAnimeSourceTab(),
        ),
        searchQuery = extensionsState.searchQuery,
        onChangeSearchQuery = extensionsScreenModel::search,
    )
}

@Composable
internal fun Screen.AnimeUpdatesScreen() {
    val context = LocalContext.current
    SingleTabScreen(
        titleRes = MR.strings.label_recent_updates,
        tab = animeUpdatesTab(context, fromMore = false),
    )
}

@Composable
internal fun Screen.AnimeDownloadsScreen() {
    // The anime queue is a RecyclerView screen and drives its own FAB from the scroll connection it
    // is given. Nothing above it needs that here, so it gets one that only forwards.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
        }
    }
    SingleTabScreen(
        titleRes = MR.strings.label_download_queue,
        tab = animeDownloadTab(nestedScrollConnection),
    )
}
