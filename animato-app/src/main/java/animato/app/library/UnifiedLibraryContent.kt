package animato.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import animato.ui.entries.ItemCover
import animato.ui.library.LazyLibraryGrid
import animato.ui.library.UnviewedBadge
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Both libraries in one grid.
 *
 * The claim Animato makes is that anime and manga are one collection rather than two apps sharing a
 * binary, and this is the screen that has to earn it. Everything on it is deliberately about state
 * rather than about which half an entry came from: the chips ask what you are in the middle of, the
 * scope asks which shelf, and the only thing that says "anime" or "manga" is a mark on the cover.
 */
@Composable
internal fun UnifiedLibraryContent() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = viewModel { UnifiedLibraryScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(MR.strings.label_library)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                scrollBehavior = scrollBehavior,
                actions = {
                    SortMenuButton(
                        selected = state.sortMode,
                        onSelect = screenModel::setSortMode,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

        LazyLibraryGrid(
            columns = 0,
            contentPadding = contentPadding,
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = { "filters" }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = MaterialTheme.padding.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LibraryStatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter == state.statusFilter,
                            onClick = { screenModel.setStatusFilter(filter) },
                            label = { Text(stringResource(filter.labelRes)) },
                        )
                    }
                }
            }

            if (state.categoryOptions.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = { "scope" }) {
                    CategoryScopeButton(
                        options = state.categoryOptions,
                        selected = state.categoryScope,
                        onSelect = screenModel::setCategoryScope,
                    )
                }
            }

            if (state.visibleEntries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, contentType = { "empty" }) {
                    EmptyScreen(stringRes = MR.strings.information_no_entries_found)
                }
                return@LazyLibraryGrid
            }

            items(
                count = state.visibleEntries.size,
                key = { index ->
                    val entry = state.visibleEntries[index]
                    "${entry.contentType}-${entry.entryId}"
                },
                contentType = { "entry" },
            ) { index ->
                val entry = state.visibleEntries[index]
                UnifiedLibraryItem(
                    entry = entry,
                    onClick = {
                        navigator.push(
                            when (entry.contentType) {
                                ContentType.MANGA -> MangaScreen(entry.entryId)
                                ContentType.ANIME -> AnimeScreen(entry.entryId)
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The category scope.
 *
 * A button rather than a chip because there can be dozens of categories and they are not a row.
 * Each entry says which library it belongs to, since the same name can exist in both and they are
 * different shelves.
 */
@Composable
private fun CategoryScopeButton(
    options: List<CategoryScopeOption>,
    selected: CategoryScope,
    onSelect: (CategoryScope) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val allLabel = stringResource(AYMR.strings.label_category) + ": " + stringResource(AYMR.strings.label_all)
    val selectedLabel = options.firstOrNull { it.scope == selected }
        ?.takeIf { it.scope != CategoryScope.All }
        ?.let { option -> "${option.name} · ${option.contentType?.label().orEmpty()}" }
        ?: allLabel

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = selectedLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (option.contentType) {
                                null -> allLabel
                                else -> "${option.name} · ${option.contentType.label()}"
                            },
                        )
                    },
                    onClick = {
                        onSelect(option.scope)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenuButton(
    selected: LibrarySortMode,
    onSelect: (LibrarySortMode) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(
            text = stringResource(selected.labelRes),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        LibrarySortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(stringResource(mode.labelRes)) },
                onClick = {
                    onSelect(mode)
                    expanded = false
                },
            )
        }
    }
}

/**
 * One cover, with the unread count and a mark saying which library it is from.
 *
 * The title and the item count sit below the cover rather than over it, which is where
 * `docs/BRANDING.md` puts them and why this is not Mihon's grid item: a mixed grid needs the type
 * mark on the cover, and a cover carrying an overlay title, an unread badge and a type mark at once
 * is too busy to scan.
 */
@Composable
private fun UnifiedLibraryItem(
    entry: LibraryEntry,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box {
            ItemCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = entry.coverData,
                contentDescription = entry.title,
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(BadgePadding),
                horizontalArrangement = Arrangement.spacedBy(BadgePadding),
            ) {
                UnviewedBadge(count = entry.unviewedItems)
                ContentTypeBadge(entry.contentType)
            }
        }
        Text(
            text = entry.title,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                when (entry.contentType) {
                    ContentType.MANGA -> MR.strings.display_mode_chapter
                    ContentType.ANIME -> AYMR.strings.display_mode_episode
                },
                entry.totalItems.toString(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The one place the grid says which library an entry came from.
 *
 * A letter rather than an icon: at cover-badge size an icon for "manga" and an icon for "anime"
 * are two similar smudges, and the initial is legible at any density in any locale that uses it.
 */
@Composable
private fun ContentTypeBadge(contentType: ContentType) {
    Badge(
        text = stringResource(
            when (contentType) {
                ContentType.MANGA -> AYMR.strings.label_manga
                ContentType.ANIME -> AYMR.strings.label_anime
            },
        ),
        color = MaterialTheme.colorScheme.secondary,
        textColor = MaterialTheme.colorScheme.onSecondary,
    )
}

@Composable
private fun ContentType.label(): String = stringResource(
    when (this) {
        ContentType.MANGA -> AYMR.strings.label_manga
        ContentType.ANIME -> AYMR.strings.label_anime
    },
)

private val BadgePadding = 4.dp
