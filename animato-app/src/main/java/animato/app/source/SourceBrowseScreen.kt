package animato.app.source

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.content.SourceCategory
import animato.app.entry.EntryScreen
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.entries.ItemCover
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.seconds

/**
 * One source, browsed the way the rest of this app browses.
 *
 * A device arrived here from the extensions list and listed four faults with upstream's version of
 * this screen: no pull to refresh, nothing to sort by, a search unlike the app's own, and filters
 * behind a control in an awkward place. This is the answer to all four in one layout:
 *
 * - the same filled-pill field the search and Discover screens use, at the top where it is on both;
 * - the listings and the filters as **chips on one row** — Popular, Latest, Filters — because that
 *   is what "sort" means to a source: which of its own lists you are reading, and what it is
 *   filtered by. A source has no generic sort of its own; the sort it does have lives inside its
 *   filter tree, which the sheet draws;
 * - pull to refresh over the grid, which re-asks the current question from page one;
 * - and a long press that puts a cover in the library without leaving the page.
 *
 * The filter sheet itself is upstream's. It renders a tree each extension composes freely, it is
 * pure UI over a public model, and a second implementation of it would be a second set of bugs.
 */
class SourceBrowseScreen(
    /**
     * Public because the incognito banner and the leave-incognito rule both ask the top screen
     * which source it is showing — the same question upstream's browse screen answers.
     */
    val sourceId: Long,
    private val contentType: ContentType,
) : Screen() {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = viewModel(key = "source-browse-$contentType-$sourceId") {
            SourceBrowseScreenModel(sourceId = sourceId, contentType = contentType)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var filtersOpen by remember { mutableStateOf(false) }
        var categoriesOpen by remember { mutableStateOf(false) }
        var isRefreshing by rememberSaveable { mutableStateOf(false) }

        val gridState = rememberLazyGridState()
        // Asking for the next page a screenful early, so the grid rarely stops at a spinner.
        val nearEnd by remember {
            derivedStateOf {
                val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= gridState.layoutInfo.totalItemsCount - PREFETCH_DISTANCE
            }
        }
        LaunchedEffect(nearEnd, state.items.size) {
            if (nearEnd) screenModel.loadMore()
        }

        if (categoriesOpen) {
            CategorySheet(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                onSelect = {
                    categoriesOpen = false
                    screenModel.selectCategory(it)
                },
                onDismiss = { categoriesOpen = false },
            )
        }

        if (filtersOpen) {
            when (contentType) {
                ContentType.MANGA -> state.mangaFilters?.let { filters ->
                    SourceFilterDialog(
                        onDismissRequest = { filtersOpen = false },
                        filters = filters,
                        onReset = screenModel::resetFilters,
                        onFilter = {
                            filtersOpen = false
                            screenModel.applyFilters()
                        },
                        onUpdate = screenModel::setMangaFilters,
                    )
                }
                ContentType.ANIME -> state.animeFilters?.let { filters ->
                    SourceFilterAnimeDialog(
                        onDismissRequest = { filtersOpen = false },
                        filters = filters,
                        onReset = screenModel::resetFilters,
                        onFilter = {
                            filtersOpen = false
                            screenModel.applyFilters()
                        },
                        onUpdate = screenModel::setAnimeFilters,
                    )
                }
            }
        }

        Scaffold(
            topBar = {
                Surface(tonalElevation = TopBarElevation) {
                    Column(modifier = Modifier.statusBarsPadding()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MaterialTheme.padding.extraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = navigator::pop) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                                )
                            }
                            TextField(
                                value = state.query,
                                onValueChange = screenModel::onQueryChange,
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = state.sourceName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (state.query.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                screenModel.onQueryChange("")
                                                screenModel.selectListing(SourceListing.POPULAR)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = stringResource(MR.strings.action_reset),
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(FieldRadius),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { screenModel.search() }),
                            )
                            state.webViewUrl?.let { url ->
                                IconButton(
                                    onClick = {
                                        context.startActivity(
                                            WebViewActivity.newIntent(context, url, sourceId, state.sourceName),
                                        )
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Public,
                                        contentDescription = stringResource(MR.strings.action_open_in_web_view),
                                    )
                                }
                            }
                        }

                        // What the source is being asked, as three chips rather than a tab strip
                        // and a hidden control: they are the same kind of choice and they read
                        // better side by side than one of them behind an icon.
                        Row(
                            modifier = Modifier.padding(
                                start = MaterialTheme.padding.medium,
                                end = MaterialTheme.padding.medium,
                                bottom = MaterialTheme.padding.small,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            FilterChip(
                                selected = state.listing == SourceListing.POPULAR,
                                onClick = { screenModel.selectListing(SourceListing.POPULAR) },
                                label = { Text(stringResource(MR.strings.popular)) },
                            )
                            if (state.supportsLatest) {
                                FilterChip(
                                    selected = state.listing == SourceListing.LATEST,
                                    onClick = { screenModel.selectListing(SourceListing.LATEST) },
                                    label = { Text(stringResource(MR.strings.latest)) },
                                )
                            }
                            if (state.hasFilters) {
                                FilterChip(
                                    selected = state.listing == SourceListing.SEARCH,
                                    onClick = { filtersOpen = true },
                                    label = { Text(stringResource(MR.strings.action_filter)) },
                                )
                            }
                        }

                        /*
                         * The source's own divisions, where the thumb is.
                         *
                         * This row is the whole point of the category work. A playlist's groups and
                         * an addon's genres were reachable only through the filter sheet: open it,
                         * find the right dropdown, scroll a list of four hundred, choose, apply.
                         * Four gestures and a scroll to reach *Sport*, which is the first thing
                         * anybody does in an IPTV app. It is one tap now.
                         *
                         * Horizontal and scrolling, with a *More* chip at the end when there are
                         * more than a screenful. A horizontal scroll through four hundred groups
                         * is not a way to find one, so past that point the sheet — which can be
                         * searched and is grouped by catalog — is the real answer and the row
                         * is a shortlist.
                         */
                        if (state.categories.isNotEmpty()) {
                            CategoryRow(
                                categories = state.categories,
                                selectedId = state.selectedCategoryId,
                                onSelect = screenModel::selectCategory,
                                onSeeAll = { categoriesOpen = true },
                            )
                        }
                    }
                }
            },
        ) { contentPadding ->
            PullRefresh(
                refreshing = isRefreshing,
                enabled = true,
                onRefresh = {
                    screenModel.reload()
                    scope.launch {
                        isRefreshing = true
                        delay(1.seconds)
                        isRefreshing = false
                    }
                },
                indicatorPadding = contentPadding,
            ) {
                when {
                    state.isLoading -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    // The source's own words, which for a 403 or a Cloudflare page are the most
                    // informative thing anyone is going to get.
                    state.failure != null -> AnimatoEmptyState(
                        message = state.failure!!,
                        modifier = Modifier.padding(contentPadding),
                        actionLabel = stringResource(MR.strings.action_retry),
                        onAction = screenModel::reload,
                    )

                    state.isEmpty -> AnimatoEmptyState(
                        message = stringResource(AYMR.strings.source_browse_empty),
                        modifier = Modifier.padding(contentPadding),
                        actionLabel = stringResource(MR.strings.action_retry),
                        onAction = screenModel::reload,
                    )

                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = GridItemMinWidth),
                        contentPadding = contentPadding + PaddingValues(MaterialTheme.padding.medium),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(items = state.items, key = { it.entryId }) { item ->
                            Column(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        navigator.push(EntryScreen(item.entryId, contentType, fromSource = true))
                                    },
                                    onLongClick = { screenModel.toggleFavorite(item) },
                                ),
                            ) {
                                Box {
                                    ItemCover.Book(
                                        modifier = Modifier.fillMaxWidth(),
                                        data = item.coverData,
                                        contentDescription = item.title,
                                        shape = RoundedCornerShape(CoverRadius),
                                    )
                                    // Already yours, said on the cover: without it the same title
                                    // looks identical whether or not a long press has been spent
                                    // on it.
                                    if (item.favorite) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = stringResource(MR.strings.in_library),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(MaterialTheme.padding.extraSmall)
                                                .size(FavoriteBadgeSize),
                                        )
                                    }
                                }
                                Text(
                                    text = item.title,
                                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (item.favorite) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 2,
                                    minLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(MaterialTheme.padding.medium),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(MoreSpinnerSize))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shortlist of categories, one tap each.
 *
 * Capped, and the cap is the reason the sheet exists. A playlist with four hundred groups drawn as
 * four hundred chips is a horizontal scroll nobody reaches the end of, and it costs a layout pass
 * per chip on a row that is redrawn every time the grid scrolls. The first few are the ones a
 * provider put first, which is the closest thing an M3U file has to an order of importance.
 */
@Composable
private fun CategoryRow(
    categories: List<SourceCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onSeeAll: () -> Unit,
) {
    // The selected one is always drawn, even when it sits past the cap: a chip row that does not
    // show what is selected is a screen showing filtered results with no visible reason.
    val shown = remember(categories, selectedId) {
        val head = categories.take(CATEGORY_CHIPS)
        val selected = categories.firstOrNull { it.id == selectedId }
        if (selected == null || selected in head) head else head + selected
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.small,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        items(items = shown, key = { it.id }) { category ->
            FilterChip(
                selected = category.id == selectedId,
                onClick = { onSelect(category.id) },
                label = {
                    Text(
                        text = category.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
        if (categories.size > CATEGORY_CHIPS) {
            item(key = "see-all") {
                FilterChip(
                    selected = false,
                    onClick = onSeeAll,
                    label = { Text(stringResource(AYMR.strings.action_see_all)) },
                )
            }
        }
    }
}

/**
 * All of them, searchable, grouped the way the source grouped them.
 *
 * The search field is not decoration. Four hundred IPTV groups and a hundred and sixty Stremio
 * genres are both lists you find something in by typing, and both were previously offered as a
 * dropdown with no way to type at all.
 *
 * Headings come from [SourceCategory.group], which is how *Action in Top Movies* stays apart from
 * *Action in New Series* — two chips reading the same word, which without the heading above them
 * is a list that looks duplicated and behaves unpredictably.
 */
@Composable
private fun CategorySheet(
    categories: List<SourceCategory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matching = remember(categories, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            categories
        } else {
            categories.filter {
                it.label.contains(needle, ignoreCase = true) ||
                    it.group?.contains(needle, ignoreCase = true) == true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
            Text(
                text = stringResource(AYMR.strings.source_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(FieldRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            if (matching.isEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.source_categories_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.medium),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = MaterialTheme.padding.small),
            ) {
                // Grouped in place rather than up front: the grouping has to follow the search, and
                // a heading over nothing is worse than no heading.
                var heading: String? = null
                matching.forEach { category ->
                    if (category.group != heading) {
                        heading = category.group
                        category.group?.let { name ->
                            item(key = "group-${category.id}") {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        top = MaterialTheme.padding.small,
                                        bottom = MaterialTheme.padding.extraSmall,
                                    ),
                                )
                            }
                        }
                    }
                    item(key = category.id) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (category.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (category.id == selectedId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(category.id) }
                                .padding(vertical = MaterialTheme.padding.small),
                        )
                    }
                }
            }
        }
    }
}

private const val PREFETCH_DISTANCE = 8
private val GridItemMinWidth = 104.dp
private val CoverRadius = 12.dp
private val FieldRadius = 24.dp
private val TopBarElevation = 3.dp
private val FavoriteBadgeSize = 18.dp
private val MoreSpinnerSize = 24.dp

/** How many chips fit before the row stops being a shortcut and becomes a scroll. */
private const val CATEGORY_CHIPS = 12
