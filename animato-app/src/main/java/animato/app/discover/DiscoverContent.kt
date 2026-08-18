package animato.app.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import animato.app.search.AnimatoSearchScreen
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.entries.ItemCover
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.seconds

/**
 * Discovery that works before you have installed anything.
 *
 * Mihon's Browse opens on a list of sources, which asks you to pick a website before you can look
 * for a story. This screen opens on what the world is watching and reading — rails of public
 * metadata that need no extension at all, one per question per medium — with the search field above
 * them, because Discover begins with a query rather than with a title.
 *
 * *Your sources* sits underneath and is the only part that can be empty. That ordering is the whole
 * design: a fresh install used to open here on nothing but a sentence explaining the nothing.
 *
 * A metadata title has no source, so tapping one cannot open it. It searches instead, across
 * whatever is installed of that medium — which is honest, and is also the moment where wanting a
 * source becomes the user's own idea rather than a thing the app demanded up front.
 */
@Composable
internal fun DiscoverContent() {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val screenModel = viewModel { DiscoverScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }

    // One search screen for the whole app: the library first, then every source separately. The
    // per-half global searches this used to push are what made "I can't search to add an anime"
    // true, since which one you got depended on where you had come from.
    val search: (String, ContentType?) -> Unit = { text, restrictTo ->
        if (text.isNotBlank()) navigator.push(AnimatoSearchScreen(text, restrictTo))
    }

    val openSourceItem: (DiscoverItem) -> Unit = { item ->
        scope.launch {
            val id = withIOContext { screenModel.resolveEntryId(item) }
            navigator.push(EntryScreen(id, item.contentType, fromSource = true))
        }
    }

    Scaffold { contentPadding ->
        var isRefreshing by rememberSaveable { mutableStateOf(false) }
        PullRefresh(
            refreshing = isRefreshing,
            enabled = true,
            onRefresh = {
                screenModel.refresh()
                scope.launch {
                    isRefreshing = true
                    delay(1.seconds)
                    isRefreshing = false
                }
            },
            indicatorPadding = contentPadding,
        ) {
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                item(key = "search") {
                    SearchRow(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = { search(query, null) },
                    )
                }

                state.metadataRails.forEach { rail ->
                    // A metadata title has no source, so the only thing a tap can mean is "find me this
                    // in what I have". Restricted to the rail's own medium: a trending anime has no
                    // business being looked for in a manga source, and asking every source of both
                    // halves is how a search takes twice as long to say nothing.
                    metadataRail(
                        rail = rail,
                        showMedium = state.lens == ContentFilter.ALL,
                        onViewAll = { navigator.push(MetadataGridScreen(rail.rail, rail.contentType)) },
                    ) { item ->
                        search(item.title, rail.contentType)
                    }
                }

                /*
                 * The way in to managing sources, attached to the only heading on the screen that
                 * is already about them.
                 *
                 * It was a row at the very bottom, under everything — and a device asked where it
                 * should go instead: the top of the page, or somewhere else? Neither. The top
                 * belongs to the search field, and a management link there competes with the one
                 * thing this screen is for. Here it reads as what it is: these are your sources,
                 * and this is where you change them.
                 */
                item(key = "your-sources") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            text = stringResource(AYMR.strings.label_your_sources),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { navigator.push(ExtensionsScreen()) },
                            modifier = Modifier.padding(end = MaterialTheme.padding.small),
                        ) {
                            Text(stringResource(AYMR.strings.action_manage_sources))
                        }
                    }
                }

                if (!state.hasSources) {
                    item(key = "no-sources") {
                        NoSourcesCard(onAddSources = { navigator.push(ExtensionsScreen()) })
                    }
                } else {
                    sourceRail("popular", MR.strings.popular, state.popular, openSourceItem)
                    sourceRail("latest", MR.strings.latest, state.latest, openSourceItem)
                }
            }
        }
    }
}

/**
 * The search field, with the lens beside it rather than inside it.
 *
 * A real field and not a title bar with a magnifier: this screen starts with a query, and a control
 * you have to tap once to reveal is a control most people never find. The lens keeps the top-right
 * slot it has on Home and Library, so it is in the same place on every screen that lists content.
 */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        // A filled pill, matching the search screen's field. It was an outlined box, and on a dark
        // background the strongest line on the page belonged to an empty rectangle — the exact
        // fault the search screen had already fixed, reported again from a device about this one.
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = stringResource(AYMR.strings.discover_search_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(SearchFieldRadius),
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

/**
 * One public rail: one question, about one medium.
 *
 * ## The medium is in the header, not on the covers
 *
 * Each rail holds a single medium now, so the header carries the mark — *Trending · Anime* — and the
 * cards carry none. Under a narrowed lens even the header drops it, which is the same rule the rest
 * of the app follows: when everything on screen is the same kind, saying so on each item is noise.
 *
 * *View all* exists now — the note that used to explain its absence said it "comes back with the
 * browse-a-rail screen", and [MetadataGridScreen] is that screen. It is also where the questions
 * the front page no longer lists still live.
 */
private fun LazyListScope.metadataRail(
    rail: MetadataRailState,
    showMedium: Boolean,
    onViewAll: () -> Unit,
    onClick: (MetadataItem) -> Unit,
) {
    if (!rail.isLoading && rail.items.isEmpty()) return

    item(key = "header-${rail.key}") {
        val question = stringResource(rail.rail.labelRes())
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(
                text = if (showMedium) {
                    "$question · ${stringResource(rail.contentType.labelRes())}"
                } else {
                    question
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onViewAll,
                modifier = Modifier.padding(end = MaterialTheme.padding.small),
            ) {
                Text(stringResource(AYMR.strings.discover_view_all))
            }
        }
    }
    item(key = "rail-${rail.key}") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(items = rail.items, key = { it.key }) { item ->
                MetadataCard(item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun MetadataCard(item: MetadataItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(RailItemWidth)
            .tvClickable(onClick = onClick),
    ) {
        ItemCover.Book(
            modifier = Modifier.fillMaxWidth(),
            data = item.coverUrl,
            contentDescription = item.title,
            shape = RoundedCornerShape(CoverRadius),
        )
        Text(
            text = item.title,
            modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.caption.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun LazyListScope.sourceRail(
    id: String,
    titleRes: StringResource,
    rail: DiscoverRail,
    onClick: (DiscoverItem) -> Unit,
) {
    if (!rail.isLoading && rail.items.isEmpty() && rail.failedSources.isEmpty()) return

    item(key = "source-header-$id") {
        SectionHeader(stringResource(titleRes))
    }
    item(key = "source-rail-$id") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(items = rail.items, key = { it.key }) { item ->
                Column(
                    modifier = Modifier
                        .width(RailItemWidth)
                        .tvClickable { onClick(item) },
                ) {
                    ItemCover.Book(
                        modifier = Modifier.fillMaxWidth(),
                        data = item.coverData,
                        contentDescription = item.title,
                        shape = RoundedCornerShape(CoverRadius),
                    )
                    Text(
                        text = item.title,
                        modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (rail.failedSources.isNotEmpty()) {
        item(key = "source-failures-$id") {
            // Named rather than swallowed: a rail that is quietly short reads as "there is nothing",
            // which is a different thing from "these sources did not answer".
            Text(
                text = rail.failedSources.joinToString(),
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * No sources yet, said without apology.
 *
 * The sentence does two things at once: says that nothing above it is broken, and says what a
 * source would be *for*. "You don't have any sources" does neither, and reads like a fault.
 */
@Composable
private fun NoSourcesCard(onAddSources: () -> Unit) {
    AnimatoEmptyState(
        message = stringResource(AYMR.strings.discover_no_sources),
        actionLabel = stringResource(AYMR.strings.action_add_sources),
        onAction = onAddSources,
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = MaterialTheme.padding.medium),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

internal fun MetadataRail.labelRes(): StringResource = when (this) {
    MetadataRail.TRENDING -> AYMR.strings.rail_trending
    MetadataRail.THIS_SEASON -> AYMR.strings.rail_this_season
    MetadataRail.TOP_RATED -> AYMR.strings.rail_top_rated
}

internal fun ContentType.labelRes(): StringResource = when (this) {
    ContentType.MANGA -> AYMR.strings.label_manga
    ContentType.ANIME -> AYMR.strings.label_anime
}

private val RailItemWidth = 112.dp
private val CoverRadius = 12.dp
private val SearchFieldRadius = 24.dp
