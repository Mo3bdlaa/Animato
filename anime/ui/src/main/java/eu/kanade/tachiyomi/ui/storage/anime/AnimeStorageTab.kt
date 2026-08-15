package eu.kanade.tachiyomi.ui.storage.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.domain.category.AnimeCategory
import animato.ui.storage.StorageScreenContent
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.animeStorageTab(): TabContent {
    val screenModel = viewModel { AnimeStorageScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        content = { contentPadding, _ ->
            StorageScreenContent(
                state = state,
                itemCountPlural = AYMR.plurals.anime_num_episodes,
                deleteConfirmationTitle = AYMR.strings.delete_downloads_for_anime,
                uncategorizedId = AnimeCategory.UNCATEGORIZED_ID,
                contentPadding = contentPadding,
                onCategorySelected = screenModel::setSelectedCategory,
                onDelete = screenModel::deleteEntry,
            )
        },
    )
}
