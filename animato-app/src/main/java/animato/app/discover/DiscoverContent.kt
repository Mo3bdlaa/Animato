package animato.app.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.navigation.contentType
import animato.domain.content.ContentType
import animato.ui.entries.ItemCover
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
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
 * Discovery by content rather than by source.
 *
 * Mihon's Browse opens on a list of sources, which asks you to pick a website before you can look
 * for a story. Here the app bar is the search field — one search across every installed source —
 * and under it are the things those sources are currently offering. The source and extension lists
 * are still one tap away, because they are settings for this screen rather than the way in.
 */
@Composable
internal fun DiscoverContent() {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val type = contentType()
    val screenModel = viewModel(key = "discover-$type") { DiscoverScreenModel(contentType = type) }
    val state by screenModel.state.collectAsStateWithLifecycle()

    val openItem: (DiscoverItem) -> Unit = { item ->
        scope.launch {
            val id = withIOContext { screenModel.resolveEntryId(item) }
            navigator.push(
                when (item.contentType) {
                    ContentType.MANGA -> MangaScreen(id, true)
                    ContentType.ANIME -> AnimeScreen(id, true)
                },
            )
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(AYMR.strings.label_discover)) },
                searchQuery = null,
                onChangeSearchQuery = {},
                placeholderText = stringResource(MR.strings.action_global_search),
                onSearch = { query ->
                    navigator.push(
                        when (type) {
                            ContentType.MANGA -> GlobalSearchScreen(query)
                            ContentType.ANIME -> GlobalAnimeSearchScreen(query)
                        },
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            if (!state.hasPinnedSources) {
                item {
                    Text(
                        text = stringResource(MR.strings.no_pinned_sources),
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                rail(MR.strings.popular, state.popular, openItem)
                rail(MR.strings.latest, state.latest, openItem)
            }

            item {
                DestinationRow(
                    labelRes = MR.strings.label_sources,
                    icon = Icons.Outlined.TravelExplore,
                    onClick = { navigator.push(BrowseCatalogScreen()) },
                )
            }
            item {
                DestinationRow(
                    labelRes = MR.strings.label_extensions,
                    icon = Icons.Outlined.Extension,
                    onClick = { navigator.push(BrowseCatalogScreen(toExtensions = true)) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rail(
    titleRes: StringResource,
    rail: DiscoverRail,
    onClick: (DiscoverItem) -> Unit,
) {
    if (!rail.isLoading && rail.items.isEmpty() && rail.failedSources.isEmpty()) return

    item {
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(items = rail.items, key = { it.key }) { item ->
                Column(
                    modifier = Modifier
                        .width(RailItemWidth)
                        .clickable { onClick(item) },
                ) {
                    ItemCover.Book(
                        modifier = Modifier.fillMaxWidth(),
                        data = item.coverData,
                        contentDescription = item.title,
                    )
                    Text(
                        text = item.title,
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
    if (rail.failedSources.isNotEmpty()) {
        item {
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

@Composable
private fun DestinationRow(
    labelRes: StringResource,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        headlineContent = { Text(stringResource(labelRes)) },
        trailingContent = {
            Icon(imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        },
    )
}

private val RailItemWidth = 112.dp
