package animato.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tachiyomi.presentation.core.components.material.TabText

/**
 * The category tab strip above a library.
 *
 * Generic for the same reason [animato.ui.category.ChangeCategoryDialog] is: manga and anime
 * categories are separate models, and the strip only ever needs a label and a count. [label] is
 * composable because the default category's displayed name is a translated string.
 */
@Composable
fun <T> LibraryTabs(
    categories: List<T>,
    pagerState: PagerState,
    getNumberOfItemsForCategory: (T) -> Int?,
    onTabItemClick: (Int) -> Unit,
    label: @Composable (T) -> String,
) {
    val currentPageIndex = pagerState.currentPage.coerceAtMost(categories.lastIndex)
    Column(
        modifier = Modifier.zIndex(1f),
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = currentPageIndex,
            edgePadding = 0.dp,
            // TODO: use default when width is fixed upstream
            // https://issuetracker.google.com/issues/242879624
            divider = {},
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = currentPageIndex == index,
                    onClick = { onTabItemClick(index) },
                    text = {
                        TabText(
                            text = label(category),
                            badgeCount = getNumberOfItemsForCategory(category),
                        )
                    },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        HorizontalDivider()
    }
}
