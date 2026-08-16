package animato.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.navigation.LensButton
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import animato.ui.components.Pill
import animato.ui.components.UnviewedPill
import animato.ui.entries.ItemCover
import animato.ui.library.LazyLibraryGrid
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Both libraries in one grid.
 *
 * The claim Animato makes is that anime and manga are one collection rather than two apps sharing a
 * binary, and this is the screen that has to earn it.
 *
 * It earns it by having exactly one control per question, and by never asking two of them in the
 * same shape. Which half of the collection is the **lens**, in the top bar, global, and shown by
 * the icon rather than by a word. Which shelf is the **category chips**, one row, user categories
 * only. What state a title is in — unread, downloaded, tracked — is the **filter sheet**, because a
 * title can be several of those at once and a chip row promises one at a time.
 *
 * The previous version put all three in the chip row: `All · Reading · Watching · Completed ·
 * Unread · Downloaded`, which mixed a medium, a state and a derived state into one row of things
 * that looked identical and behaved nothing alike. Watching was a lens. Downloaded was a filter.
 * They were chips because chips were what the row had.
 */
@Composable
internal fun UnifiedLibraryContent() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = viewModel { UnifiedLibraryScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(MR.strings.label_library)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                scrollBehavior = scrollBehavior,
                actions = {
                    LensButton()
                    IconButton(onClick = { sheetOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = stringResource(MR.strings.action_filter),
                            // The only tell that the sheet is doing something. A filtered grid has
                            // to look filtered from the top bar, or the first question is why the
                            // library is empty.
                            tint = if (state.filters.any) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = remember { SnackbarHostState() }) },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

        val layoutDirection = LocalLayoutDirection.current
        Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
            // Fixed rather than scrolling with the grid: switching shelf is the main thing this
            // screen does, and a control that leaves the screen after two flicks is not available.
            if (state.visibleCategories.isNotEmpty()) {
                CategoryChipRow(
                    categories = state.visibleCategories,
                    selected = state.activeCategory?.name,
                    shelfSize = state.shelfEntries.size,
                    onSelect = screenModel::selectCategory,
                )
            }

            LazyLibraryGrid(
                columns = state.columns,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
            ) {
                if (state.visibleEntries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = { "empty" }) {
                        LibraryEmptyState(
                            emptiedBySettings = state.emptiedBySettings,
                            onReset = screenModel::resetDisplay,
                        )
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
                    LibraryGridItem(
                        entry = entry,
                        showTypeChip = state.lens == ContentFilter.ALL,
                        showUnviewedCount = state.showUnviewedCount,
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

    if (sheetOpen) {
        LibraryDisplaySheet(
            state = state,
            onDismiss = { sheetOpen = false },
            onSortMode = screenModel::setSortMode,
            onFilters = screenModel::setFilters,
            onColumns = screenModel::setColumns,
            onShowUnviewedCount = screenModel::setShowUnviewedCount,
            onReset = screenModel::resetDisplay,
        )
    }
}

/**
 * The shelves, as one row.
 *
 * The count rides on the selected chip and nowhere else. Four chips each carrying a number is four
 * numbers competing to be read, and the one anybody wants is the one belonging to the shelf they
 * are standing in.
 */
@Composable
private fun CategoryChipRow(
    categories: List<LibraryCategory>,
    selected: String?,
    shelfSize: Int,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryChip(
            label = stringResource(AYMR.strings.label_all),
            count = shelfSize.takeIf { selected == null },
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        categories.forEach { category ->
            CategoryChip(
                label = category.name,
                count = shelfSize.takeIf { selected == category.name },
                selected = selected == category.name,
                onClick = { onSelect(category.name) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, maxLines = 1)
                if (count != null) {
                    Text(
                        text = count.toString(),
                        modifier = Modifier.secondaryItemAlpha(),
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

/**
 * One cover, and the two things worth saying over it.
 *
 * The type mark appears only under the All lens: under Anime or Manga every cover on screen is the
 * same kind, and a label repeated on every item in a grid is not information. That rule is the
 * lens's whole payoff — narrowing removes a mark rather than adding a banner.
 *
 * Title and item count sit below the cover, two lines reserved whether or not the title needs them.
 * A grid ellipsizes constantly and rows have to line up; a text-dependent height means every row
 * after a long title is a different height from the one above it.
 */
@Composable
private fun LibraryGridItem(
    entry: LibraryEntry,
    showTypeChip: Boolean,
    showUnviewedCount: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.tvClickable(onClick = onClick)) {
        Box {
            ItemCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = entry.coverData,
                contentDescription = entry.title,
                shape = RoundedCornerShape(CoverRadius),
            )
            if (showUnviewedCount) {
                UnviewedPill(
                    count = entry.unviewedItems,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PillInset),
                )
            }
            if (showTypeChip) {
                ContentTypePill(
                    contentType = entry.contentType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(PillInset),
                )
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
                    ContentType.MANGA -> AYMR.strings.caption_chapters
                    ContentType.ANIME -> AYMR.strings.caption_episodes
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

/** The one place the grid says which half an entry came from, and only while both are on screen. */
@Composable
private fun ContentTypePill(contentType: ContentType, modifier: Modifier = Modifier) {
    Pill(
        text = stringResource(
            when (contentType) {
                ContentType.MANGA -> AYMR.strings.label_manga
                ContentType.ANIME -> AYMR.strings.label_anime
            },
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * One sheet: Sort, Filter, Display.
 *
 * No Apply button. Everything here takes effect as it is tapped and the grid is visible behind the
 * sheet, so Apply would only be a second thing to remember to press. That also leaves the screen's
 * one primary-action slot free, which the brand sheet spends on nothing here — a library that is
 * merely being looked through has no primary action, and inventing one to fill the space is how
 * screens get loud.
 *
 * Reset is outlined and last, because it undoes rather than does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryDisplaySheet(
    state: UnifiedLibraryState,
    onDismiss: () -> Unit,
    onSortMode: (LibrarySortMode) -> Unit,
    onFilters: (LibraryFilters) -> Unit,
    onColumns: (Int) -> Unit,
    onShowUnviewedCount: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius),
    ) {
        Column(modifier = Modifier.padding(bottom = MaterialTheme.padding.large)) {
            SheetSection(stringResource(MR.strings.action_sort))
            LibrarySortMode.entries.forEach { mode ->
                SheetRadioRow(
                    label = stringResource(mode.labelRes),
                    selected = mode == state.sortMode,
                    onClick = { onSortMode(mode) },
                )
            }

            HorizontalDivider()
            SheetSection(stringResource(MR.strings.action_filter))
            SheetCheckRow(
                label = stringResource(AYMR.strings.filter_unviewed_only),
                checked = state.filters.unviewedOnly,
                onCheckedChange = { onFilters(state.filters.copy(unviewedOnly = it)) },
            )
            SheetCheckRow(
                label = stringResource(MR.strings.label_downloaded),
                checked = state.filters.downloadedOnly,
                onCheckedChange = { onFilters(state.filters.copy(downloadedOnly = it)) },
            )
            SheetCheckRow(
                label = stringResource(MR.strings.action_filter_tracked),
                checked = state.filters.trackedOnly,
                onCheckedChange = { onFilters(state.filters.copy(trackedOnly = it)) },
            )

            HorizontalDivider()
            SheetSection(stringResource(MR.strings.pref_category_display))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(AYMR.strings.label_columns))
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    UnifiedLibraryPreferences.COLUMN_CHOICES.forEach { choice ->
                        FilterChip(
                            selected = choice == state.columns,
                            onClick = { onColumns(choice) },
                            label = { Text(choice.toString()) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(AYMR.strings.pref_show_unviewed_count))
                Switch(checked = state.showUnviewedCount, onCheckedChange = onShowUnviewedCount)
            }

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Text(stringResource(MR.strings.action_reset))
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            top = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.extraSmall,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun SheetRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label)
    }
}

@Composable
private fun SheetCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = label)
    }
}

/**
 * Why the grid is empty, in a sentence that names the cause.
 *
 * The two reasons are different and the difference is the whole point: a library with nothing in it
 * needs somewhere to go, and a library hidden behind a filter needs the filter turned off. Telling
 * someone "no entries found" when they set a filter two taps ago is the app pretending not to know.
 */
@Composable
private fun LibraryEmptyState(
    emptiedBySettings: Boolean,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(
                if (emptiedBySettings) AYMR.strings.library_empty_filtered else AYMR.strings.library_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (emptiedBySettings) {
            OutlinedButton(onClick = onReset) {
                Text(stringResource(MR.strings.action_reset))
            }
        }
    }
}

private val CoverRadius = 12.dp
private val PillInset = 6.dp
private val SheetRadius = 28.dp
