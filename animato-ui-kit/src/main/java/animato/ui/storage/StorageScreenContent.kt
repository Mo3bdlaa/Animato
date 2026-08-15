package animato.ui.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * How much space one library's downloads take, per entry.
 *
 * The screen is written against [StorageCategory] and [StorageItem] rather than a library's own
 * models, so the manga and anime tabs can both render through it.
 */
@Composable
fun StorageScreenContent(
    state: StorageScreenState,
    itemCountPlural: PluralsResource,
    deleteConfirmationTitle: StringResource,
    uncategorizedId: Long,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onCategorySelected: (StorageCategory) -> Unit,
    onDelete: (Long) -> Unit,
) {
    when (state) {
        is StorageScreenState.Loading -> {
            LoadingScreen(modifier)
        }

        is StorageScreenState.Success -> {
            @Composable
            fun Info(modifier: Modifier = Modifier) {
                Column(
                    modifier = modifier,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    content = {
                        SelectStorageCategory(
                            selectedCategory = state.selectedCategory,
                            categories = state.categories,
                            uncategorizedId = uncategorizedId,
                            onCategorySelected = onCategorySelected,
                        )
                        CumulativeStorage(
                            modifier = Modifier
                                .padding(
                                    horizontal = MaterialTheme.padding.small,
                                    vertical = MaterialTheme.padding.medium,
                                )
                                .run {
                                    if (isTabletUi()) {
                                        this
                                    } else {
                                        padding(bottom = MaterialTheme.padding.medium)
                                    }
                                },
                            items = state.items,
                        )
                    },
                )
            }

            Row(
                modifier = modifier
                    .padding(horizontal = MaterialTheme.padding.small)
                    .padding(contentPadding),
                content = {
                    if (isTabletUi()) {
                        Info(
                            modifier = Modifier
                                .weight(2f)
                                .padding(end = MaterialTheme.padding.extraLarge)
                                .fillMaxHeight(),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.weight(3f),
                        content = {
                            item {
                                Spacer(Modifier.height(MaterialTheme.padding.small))
                            }
                            item {
                                if (!isTabletUi()) {
                                    Info()
                                }
                            }
                            items(
                                state.items.size,
                                itemContent = { index ->
                                    StorageItem(
                                        item = state.items[index],
                                        itemCountPlural = itemCountPlural,
                                        deleteConfirmationTitle = deleteConfirmationTitle,
                                        onDelete = onDelete,
                                    )
                                    Spacer(Modifier.height(MaterialTheme.padding.medium))
                                },
                            )
                        },
                    )
                },
            )
        }
    }
}
