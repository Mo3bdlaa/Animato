package animato.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.settings.AnimatoSettingsScreen
import animato.domain.content.ContentType
import animato.ui.entries.ItemCover
import animato.ui.navigation.AnimatoNavigator
import animato.ui.navigation.AnimatoTab
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * The first screen: where you left off, and how much you have.
 *
 * Mihon opens on the library, which asks "which of these hundreds" before it asks anything else.
 * Home answers the more common question first — the next chapter or the next episode of the thing
 * you were already part-way through — and mixes both content types in one rail, because at the
 * moment of resuming nobody is thinking about which app half they are in.
 *
 * Two sections and no more: what you were in the middle of, and what arrived since. The library
 * counts that used to sit under them are gone — four numbers nobody acts on, paid for in the space
 * a session actually needs. The counts belong to Library, where the numbers are the content.
 *
 * The lens is the button in the top bar, and the Continue rail is filtered through it. That rail is
 * where both halves meet, so it is the one list in the app where "which medium" has to be asked
 * rather than assumed — and not asking is exactly how a screen headed *Anime* came to show manga
 * chapters.
 */
@Composable
internal fun HomeScreenContent() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = viewModel { HomeScreenModel() }
    val state by screenModel.state.collectAsState()
    val lens = contentLens()
    val searchType = contentTypeOrDefault()

    // The rail is the one place both halves meet, so it is the one place the lens has to be applied
    // rather than assumed. Every item already carries its own type; nothing used to read it.
    val continueItems = remember(state.continueItems, lens) {
        state.continueItems.filter { lens.admits(it.contentType) }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = { AppBarTitle(stringResource(MR.strings.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            navigator.push(
                                when (searchType) {
                                    ContentType.MANGA -> GlobalSearchScreen()
                                    ContentType.ANIME -> GlobalAnimeSearchScreen()
                                },
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TravelExplore,
                            contentDescription = stringResource(MR.strings.action_global_search),
                        )
                    }
                    LensButton()
                    IconButton(onClick = { navigator.push(AnimatoSettingsScreen()) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(MR.strings.label_settings),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

        LazyColumn(
            contentPadding = contentPadding + PaddingValues(vertical = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            if (continueItems.isNotEmpty()) {
                item {
                    SectionHeader(stringResource(AYMR.strings.label_continue))
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        items(
                            items = continueItems,
                            key = { "${it.contentType}-${it.entryId}" },
                        ) { item ->
                            ContinueCard(
                                item = item,
                                onClick = {
                                    navigator.push(
                                        when (item.contentType) {
                                            ContentType.MANGA -> MangaScreen(item.entryId)
                                            ContentType.ANIME -> AnimeScreen(item.entryId)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
    )
}

@Composable
private fun ContinueCard(
    item: ContinueItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(ContinueCardWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        ItemCover.Book(
            data = item.coverData,
            contentDescription = item.title,
            onClick = onClick,
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                when (item.contentType) {
                    ContentType.MANGA -> MR.strings.display_mode_chapter
                    ContentType.ANIME -> AYMR.strings.display_mode_episode
                },
                formatItemNumber(item.itemNumber),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Chapter and episode numbers are stored as doubles; whole ones should not read as `12.0`. */
private fun formatItemNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

private val ContinueCardWidth = 112.dp
