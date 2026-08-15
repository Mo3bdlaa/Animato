package eu.kanade.tachiyomi.ui.stats.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.AnimeStatsScreenContent
import animato.anime.ui.stats.AnimeStatsScreenState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.animeStatsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    val screenModel = viewModel { AnimeStatsScreenModel() }
    val state by screenModel.state.collectAsState()

    if (state is AnimeStatsScreenState.Loading) {
        LoadingScreen()
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        content = { contentPadding, _ ->

            if (state is AnimeStatsScreenState.Loading) {
                LoadingScreen()
            } else {
                AnimeStatsScreenContent(
                    state = state as AnimeStatsScreenState.Success,
                    paddingValues = contentPadding,
                )
            }
        },
        navigateUp = navigator::pop,
    )
}
