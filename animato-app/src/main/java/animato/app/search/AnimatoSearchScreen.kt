package animato.app.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.entry.EntryScreen
import animato.app.extension.ExtensionsScreen
import animato.app.navigation.LensButton
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.components.Pill
import animato.ui.components.UnviewedPill
import animato.ui.entries.ItemCover
import animato.ui.theme.LocalAnimatoPalette
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

/**
 * One search field for the whole app.
 *
 * There was no such thing before: search meant Mihon's global manga search or the anime one,
 * reached from whichever screen you happened to be on, which is why *"I can't search to add an
 * anime"* was a fair description of the app rather than a misunderstanding of it.
 *
 * Your library comes first, always, even when it has one hit and the sources have twelve — the most
 * common search is *where is that thing I already have*. Library results are rows and source
 * results are cards, because retrieval and appraisal are different acts and a shape says so faster
 * than a heading does.
 *
 * The lens sits in the field rather than in a bar, because here the field is the bar.
 */
class AnimatoSearchScreen(
    private val initialQuery: String = "",
    /**
     * Narrows this one search to a single medium, without touching the lens.
     *
     * Set when the query came from somewhere that already knew what it was asking about — a
     * trending *anime* cover, say. Searching every manga source for it as well is work nobody asked
     * for and a screenful of empty groups to scroll past. The lens is a standing preference and a
     * tap on one cover is not the place to change it, so this is passed rather than written.
     */
    private val restrictTo: ContentType? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val screenModel = viewModel { SearchScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        // Re-fires every time this screen returns to the top — Voyager recomposes it from scratch —
        // which is why the search itself is seeded once in the model rather than run from here.
        LaunchedEffect(initialQuery, restrictTo) {
            screenModel.restrictTo(restrictTo)
            if (initialQuery.isNotBlank()) {
                screenModel.seedSearch(initialQuery)
            }
        }

        val openLibraryHit: (LibraryHit) -> Unit = { hit ->
            navigator.push(EntryScreen(hit.entryId, hit.contentType))
        }

        val openSourceHit: (SourceHit) -> Unit = { hit ->
            scope.launch {
                val id = withIOContext { screenModel.resolveEntryId(hit) }
                navigator.push(EntryScreen(id, hit.contentType, fromSource = true))
            }
        }

        Scaffold(
            topBar = {
                SearchField(
                    query = state.query,
                    onQueryChange = screenModel::onQueryChange,
                    onSearch = { screenModel.search(state.query) },
                    onBack = navigator::pop,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                if (state.isIdle) {
                    suggestions(
                        state = state,
                        onPick = { query ->
                            screenModel.onQueryChange(query)
                            screenModel.search(query)
                        },
                        onClearRecent = screenModel::clearRecent,
                    )
                    return@LazyColumn
                }

                if (state.libraryHits.isNotEmpty()) {
                    item(key = "library-header") {
                        SectionHeader(stringResource(AYMR.strings.search_in_your_library))
                    }
                    items(items = state.libraryHits, key = { "lib-${it.contentType}-${it.entryId}" }) { hit ->
                        LibraryRow(hit = hit, onClick = { openLibraryHit(hit) })
                    }
                }

                item(key = "sources-header") {
                    SourcesHeader(state)
                }

                if (!state.hasSearchableSources) {
                    item(key = "no-sources") {
                        NoSourcesToSearch(
                            lens = state.effectiveLens,
                            // Only when the lens is the reason. A search narrowed by where it came
                            // from would still find nothing after switching it, and advice that
                            // does not work is worse than none.
                            canSwitchLens = state.restrictTo == null,
                            onAddSources = { navigator.push(ExtensionsScreen()) },
                        )
                    }
                    return@LazyColumn
                }

                // Answers first. A source that found something is the only reason anybody is on
                // this screen, and it used to be listed in whatever position its name happened to
                // sort into, among nineteen rows of nought.
                state.answered.forEach { group ->
                    item(key = "group-${group.contentType}-${group.sourceId}") {
                        SourceGroupHeader(group)
                    }
                    item(key = "hits-${group.contentType}-${group.sourceId}") {
                        SourceHitRail(hits = group.hits, onClick = openSourceHit)
                    }
                }

                // Then whoever has not answered yet, so the waiting is visible without being the
                // thing in the way.
                items(
                    items = state.searching,
                    key = { "searching-${it.contentType}-${it.sourceId}" },
                ) { group ->
                    SourceGroupHeader(group)
                }

                if (state.empty.isNotEmpty()) {
                    item(key = "empty-group") {
                        CollapsedSources(
                            label = stringResource(
                                AYMR.strings.search_no_results_group,
                                state.empty.size.toString(),
                            ),
                            groups = state.empty,
                        )
                    }
                }

                if (state.failed.isNotEmpty()) {
                    item(key = "failed-group") {
                        CollapsedSources(
                            label = stringResource(
                                AYMR.strings.search_failed_group,
                                state.failed.size.toString(),
                            ),
                            groups = state.failed,
                            isError = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The field, which is the whole top bar.
 *
 * Back is a real control rather than a gesture because this screen is pushed from four different
 * places and has to be leavable from all of them the same way.
 *
 * ## The two things a device found wrong with it
 *
 * *"The search bar looks bad and it is up behind the notification bar."* Both of those, and they are
 * one fault and one choice.
 *
 * The fault: this is a `topBar`, and the shared `Scaffold` applies window insets to its **content**
 * — a top bar is expected to inset itself, the way Material's own `TopAppBar` does. This one did
 * not, so it drew under the status bar and the field's outline ran through the clock.
 *
 * The choice: an *outlined* field, on a black background, beside a plain circular button, is three
 * different shapes in one row and the strongest line on screen belongs to an empty box. It is a
 * filled pill now — a container rather than a border — which is what a search bar looks like in
 * this design language and what stops the outline competing with the results below it. The surface
 * behind it is opaque and slightly raised, so the list scrolls under something rather than into it.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(tonalElevation = TopBarElevation) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = MaterialTheme.padding.extraSmall,
                    end = MaterialTheme.padding.extraSmall,
                    bottom = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(MR.strings.action_bar_up_description),
                )
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(AYMR.strings.search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_reset),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(FieldRadius),
                // No indicator line. A filled field with an underline is both treatments at once.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            LensButton()
        }
    }
}

/**
 * Recent, then trending — because a blank field is still a screen.
 *
 * Recent queries are what you have actually looked for; trending are the same titles Discover's
 * first rail shows, offered as queries rather than as results so that a suggestion reads as one.
 */
private fun LazyListScope.suggestions(
    state: SearchState,
    onPick: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    if (state.recentQueries.isNotEmpty()) {
        item(key = "recent-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(AYMR.strings.search_recent),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onClearRecent) {
                    Text(stringResource(MR.strings.action_reset))
                }
            }
        }
        items(items = state.recentQueries, key = { "recent-$it" }) { query ->
            Text(
                text = query,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (state.trendingQueries.isNotEmpty()) {
        item(key = "trending-header") {
            SectionHeader(stringResource(AYMR.strings.search_trending))
        }
        items(items = state.trendingQueries, key = { "trending-$it" }) { query ->
            Text(
                text = query,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(query) }
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** A shelf hit: where you are in it, and one tap back into it. */
@Composable
private fun LibraryRow(hit: LibraryHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        ItemCover.Square(
            data = hit.coverData,
            contentDescription = hit.title,
            modifier = Modifier.size(ThumbSize),
            shape = RoundedCornerShape(ThumbRadius),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    text = stringResource(hit.contentType.labelRes()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    AYMR.strings.search_progress,
                    hit.viewedItems.toString(),
                    hit.totalItems.toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        UnviewedPill(count = hit.unviewedItems)
    }
}

/**
 * *From your sources*, plus how far along it is.
 *
 * A search across twenty sources takes as long as the slowest of them and arrives one source at a
 * time, so the heading has to say whether the screen is finished or merely quiet. Without it, a
 * half-loaded page and a page that found three things are the same picture.
 */
@Composable
private fun SourcesHeader(state: SearchState) {
    val done = state.sourceGroups.size - state.searching.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(AYMR.strings.search_from_your_sources),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.sourceGroups.isNotEmpty()) {
            Text(
                text = if (state.searching.isNotEmpty()) {
                    stringResource(
                        AYMR.strings.search_asking_sources,
                        done.toString(),
                        state.sourceGroups.size.toString(),
                    )
                } else {
                    stringResource(
                        AYMR.strings.search_sources_answered,
                        state.totalHits.toString(),
                        state.answered.size.toString(),
                        state.sourceGroups.size.toString(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The sources that had nothing to say, folded into one row.
 *
 * They are kept rather than hidden, because "no results from twelve sources" and "twelve sources
 * were never asked" are different facts and only one of them means the query was wrong. Folded,
 * because a device found three results underneath seventeen rows of a name and a nought.
 */
@Composable
private fun CollapsedSources(
    label: String,
    groups: List<SourceGroup>,
    isError: Boolean = false,
) {
    val palette = LocalAnimatoPalette.current
    var expanded by rememberSaveable(label) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) palette.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            groups.forEach { group -> SourceGroupHeader(group) }
        }
    }
}

/**
 * One source's heading: its name, its medium, and how it is doing.
 *
 * The type chip is here rather than on every card because a source is single-medium — this is the
 * fastest place in the app to see which half a block of results belongs to, and repeating it on
 * eight cards below would say nothing the header has not.
 */
@Composable
private fun SourceGroupHeader(group: SourceGroup) {
    val palette = LocalAnimatoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = group.sourceName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A name is not an identity: a site published in ten languages is ten sources called the
        // same thing, and without this the list reads as one source repeated ten times.
        Text(
            text = group.lang.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Pill(
            text = stringResource(group.contentType.labelRes()),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        Box(modifier = Modifier.weight(1f))
        when {
            group.isSearching -> CircularProgressIndicator(modifier = Modifier.size(SpinnerSize))
            // Named, not swallowed. A source that returned nothing and a source that returned 403
            // are different facts and only one of them is about the query.
            group.failure != null -> Text(
                text = group.failure,
                style = MaterialTheme.typography.bodySmall,
                color = palette.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text(
                text = group.hits.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceHitRail(hits: List<SourceHit>, onClick: (SourceHit) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(items = hits, key = { it.key }) { hit ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .tvClickable { onClick(hit) },
            ) {
                ItemCover.Book(
                    modifier = Modifier.fillMaxWidth(),
                    data = hit.coverData,
                    contentDescription = hit.title,
                    shape = RoundedCornerShape(CoverRadius),
                )
                Text(
                    text = hit.title,
                    modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Nothing to search, said as a cause.
 *
 * "No results" would be a lie here — nothing was asked. The sentence names which half has no
 * sources, offers the one action that fixes it, and mentions the lens, because switching it is the
 * other real answer and nobody would guess that from an empty list.
 */
@Composable
private fun NoSourcesToSearch(
    lens: ContentFilter,
    canSwitchLens: Boolean,
    onAddSources: () -> Unit,
) {
    val cause = stringResource(
        when (lens) {
            ContentFilter.ANIME -> AYMR.strings.search_no_anime_sources
            ContentFilter.MANGA -> AYMR.strings.search_no_manga_sources
            ContentFilter.ALL -> AYMR.strings.search_no_sources
        },
    )
    val lensHint = stringResource(AYMR.strings.search_switch_lens)

    AnimatoEmptyState(
        // The lens hint joins the sentence rather than sitting under the button as a third block.
        // It is part of the same thought — here is why there is nothing, and here are the two ways
        // out — and an empty state is allowed one sentence, not a paragraph with a button in it.
        message = if (canSwitchLens && lens != ContentFilter.ALL) "$cause\n$lensHint" else cause,
        actionLabel = stringResource(AYMR.strings.action_add_sources),
        onAction = onAddSources,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            top = MaterialTheme.padding.small,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

private fun ContentType.labelRes() = when (this) {
    ContentType.MANGA -> AYMR.strings.label_manga
    ContentType.ANIME -> AYMR.strings.label_anime
}

private val ThumbSize = 48.dp
private val ThumbRadius = 12.dp
private val CardWidth = 104.dp
private val CoverRadius = 12.dp
private val FieldRadius = 24.dp
private val TopBarElevation = 3.dp
private val SpinnerSize = 16.dp
