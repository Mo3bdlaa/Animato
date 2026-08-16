package animato.app.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import animato.ui.components.Pill
import animato.ui.entries.ItemCover
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
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
 * Discovery that works before you have installed anything.
 *
 * Mihon's Browse opens on a list of sources, which asks you to pick a website before you can look
 * for a story. This screen opens on what the world is watching and reading — three rails of public
 * metadata that need no extension at all — with the search field above them, because Discover
 * begins with a query rather than with a title.
 *
 * *Your sources* sits underneath and is the only part that can be empty. That ordering is the whole
 * design: a fresh install used to open here on nothing but a sentence explaining the nothing.
 *
 * A metadata title has no source, so tapping one cannot open it. It searches instead, across
 * whatever is installed — which is honest, and is also the moment where wanting a source becomes
 * the user's own idea rather than a thing the app demanded up front.
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
    val search: (String) -> Unit = { text ->
        if (text.isNotBlank()) navigator.push(AnimatoSearchScreen(text))
    }

    val openSourceItem: (DiscoverItem) -> Unit = { item ->
        scope.launch {
            val id = withIOContext { screenModel.resolveEntryId(item) }
            navigator.push(EntryScreen(id, item.contentType, fromSource = true))
        }
    }

    Scaffold { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            item(key = "search") {
                SearchRow(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { search(query) },
                )
            }

            state.metadataRails.forEach { rail ->
                metadataRail(rail) { item -> search(item.title) }
            }

            item(key = "your-sources") {
                SectionHeader(stringResource(AYMR.strings.label_your_sources))
            }

            if (!state.hasPinnedSources) {
                item(key = "no-sources") {
                    NoSourcesCard(onAddSources = { navigator.push(ExtensionsScreen()) })
                }
            } else {
                sourceRail("popular", MR.strings.popular, state.popular, openSourceItem)
                sourceRail("latest", MR.strings.latest, state.latest, openSourceItem)
            }

            item(key = "manage") {
                DestinationRow(
                    labelRes = AYMR.strings.label_sources_extensions,
                    icon = Icons.Outlined.Extension,
                    onClick = { navigator.push(ExtensionsScreen()) },
                )
            }
            item(key = "browse") {
                DestinationRow(
                    labelRes = MR.strings.label_sources,
                    icon = Icons.Outlined.TravelExplore,
                    onClick = { navigator.push(BrowseCatalogScreen()) },
                )
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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(AYMR.strings.discover_search_hint)) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(SearchFieldRadius),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        LensButton()
    }
}

/**
 * One public rail.
 *
 * No *See all*. The design sheet puts one on each header, and it would have to open a screen that
 * does not exist — a link that goes nowhere is worse than a header that promises nothing. It comes
 * back with the browse-a-rail screen.
 */
private fun LazyListScope.metadataRail(
    rail: MetadataRailState,
    onClick: (MetadataItem) -> Unit,
) {
    if (!rail.isLoading && rail.items.isEmpty()) return

    item(key = "header-${rail.rail}") {
        SectionHeader(stringResource(rail.rail.labelRes()))
    }
    item(key = "rail-${rail.rail}") {
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
        Box {
            ItemCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = item.coverUrl,
                contentDescription = item.title,
                shape = RoundedCornerShape(CoverRadius),
            )
            // These rails mix both halves under the All lens, so the mark is what tells you whether
            // you are looking at something you would watch or something you would read.
            Pill(
                text = stringResource(
                    when (item.contentType) {
                        ContentType.MANGA -> AYMR.strings.label_manga
                        ContentType.ANIME -> AYMR.strings.label_anime
                    },
                ),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(PillInset),
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Text(
            text = stringResource(AYMR.strings.discover_no_sources),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddSources) {
            Text(stringResource(AYMR.strings.action_add_sources))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun DestinationRow(
    labelRes: StringResource,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { Text(stringResource(labelRes)) },
    )
}

private fun MetadataRail.labelRes(): StringResource = when (this) {
    MetadataRail.TRENDING -> AYMR.strings.rail_trending
    MetadataRail.THIS_SEASON -> AYMR.strings.rail_this_season
    MetadataRail.TOP_RATED -> AYMR.strings.rail_top_rated
}

private val RailItemWidth = 112.dp
private val CoverRadius = 12.dp
private val PillInset = 6.dp
private val SearchFieldRadius = 24.dp
