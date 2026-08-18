package animato.app.source

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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

private const val PREFETCH_DISTANCE = 8
private val GridItemMinWidth = 104.dp
private val CoverRadius = 12.dp
private val FieldRadius = 24.dp
private val TopBarElevation = 3.dp
private val FavoriteBadgeSize = 18.dp
private val MoreSpinnerSize = 24.dp
