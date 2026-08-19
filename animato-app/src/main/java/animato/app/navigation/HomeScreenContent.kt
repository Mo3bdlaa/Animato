package animato.app.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.discover.AiringItem
import animato.app.downloads.DownloadsScreen
import animato.app.entry.EntryScreen
import animato.app.history.ContinueScreen
import animato.app.search.AnimatoSearchScreen
import animato.app.settings.AnimatoSettingsScreen
import animato.app.updates.UpdateItem
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.components.NewPill
import animato.ui.entries.ItemCover
import animato.ui.navigation.AnimatoNavigator
import animato.ui.navigation.AnimatoTab
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.seconds

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
    val scope = rememberCoroutineScope()
    val screenModel = viewModel { HomeScreenModel() }
    val state by screenModel.state.collectAsState()
    val queued by screenModel.queuedDownloads.collectAsState()
    val lens = contentLens()
    val searchType = contentTypeOrDefault()

    // The rail is the one place both halves meet, so it is the one place the lens has to be applied
    // rather than assumed. Every item already carries its own type; nothing used to read it.
    val continueItems = remember(state.continueItems, lens) {
        state.continueItems.filter { lens.accepts(it.contentType) }
    }
    val updateItems = remember(state.updateItems, lens) {
        state.updateItems.filter { lens.accepts(it.contentType) }
    }
    val airingItems = remember(state.airingItems, lens) {
        if (lens.accepts(ContentType.ANIME)) state.airingItems else emptyList()
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = { AnimatoWordmark() },
                actions = {
                    IconButton(
                        onClick = {
                            navigator.push(AnimatoSearchScreen())
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

        // The indicator is honest for a second and then leaves: the update is a background job
        // with its own notification, and a spinner that ran as long as the job would say the
        // screen is busy when it is not. Same trade Mihon's library makes, same wording.
        var isRefreshing by remember { mutableStateOf(false) }
        PullRefresh(
            refreshing = isRefreshing,
            enabled = true,
            onRefresh = {
                if (screenModel.refresh()) {
                    scope.launch {
                        isRefreshing = true
                        delay(1.seconds)
                        isRefreshing = false
                    }
                }
            },
            indicatorPadding = contentPadding,
        ) {
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(vertical = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                if (continueItems.isNotEmpty()) {
                    item {
                        // The rail is the recent handful; the screen behind this is the whole of
                        // it. Same list, same gestures — see ContinueScreen.
                        SectionHeader(
                            text = stringResource(AYMR.strings.label_continue),
                            action = stringResource(AYMR.strings.discover_view_all),
                            onActionClick = { navigator.push(ContinueScreen()) },
                        )
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
                                        navigator.push(EntryScreen(item.entryId, item.contentType))
                                    },
                                    onHide = { screenModel.hideFromContinue(item) },
                                )
                            }
                        }
                    }
                }

                /*
                 * What has not arrived yet.
                 *
                 * The rest of this screen is a record of the past — where you stopped, what turned
                 * up. Something currently airing has a next episode with a date on it, and that is
                 * the one fact about a library nothing here was showing: whether to come back on
                 * Thursday.
                 *
                 * Anime only, and only under a lens that includes anime. Manga has no schedule to
                 * read — a chapter appears when its scanlator finishes it — so there is nothing to
                 * put in the row and no honest way to fake one.
                 */
                if (airingItems.isNotEmpty()) {
                    item { SectionHeader(text = stringResource(AYMR.strings.home_airing_this_week)) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            items(items = airingItems, key = { "air-" + it.title }) { item ->
                                AiringCard(item = item)
                            }
                        }
                    }
                }

                if (updateItems.isNotEmpty()) {
                    item {
                        SectionHeader(
                            text = stringResource(AYMR.strings.label_latest_updates),
                            action = stringResource(AYMR.strings.action_see_all),
                            onActionClick = { AnimatoNavigator.openTab(AnimatoTab.UPDATES) },
                        )
                    }
                    items(
                        items = updateItems,
                        key = { "u-${it.contentType}-${it.entryId}-${it.itemName}" },
                    ) { item ->
                        UpdateRow(
                            item = item,
                            onClick = {
                                navigator.push(EntryScreen(item.entryId, item.contentType))
                            },
                        )
                    }
                }

                /*
                 * The download queue, as a line at the bottom that only exists when it has something
                 * to say.
                 *
                 * It held a slot in the bottom bar until sources took it, and a device chose where it
                 * should land: *"put it in Home as a section at the bottom — if something is
                 * downloading it stays there."* Which is the right shape for it. A queue is not a
                 * place, it is a status: worth a row while it is working, worth nothing at all when
                 * it is empty, and never worth a permanent tab.
                 */
                if (queued > 0) {
                    item(key = "downloads") {
                        DownloadsRow(
                            count = queued,
                            onClick = { navigator.push(DownloadsScreen()) },
                        )
                    }
                }

                if (continueItems.isEmpty() && updateItems.isEmpty() && queued == 0) {
                    item { EmptyShelf(onDiscover = { AnimatoNavigator.openTab(AnimatoTab.DISCOVER) }) }
                }
            }
        }
    }
}

/**
 * What the device is fetching, in one line.
 *
 * A row and not a rail: nobody browses their download queue, they check whether it is moving.
 * The count is the whole content, and tapping it opens the queue where the per-item progress,
 * the pause and the failures live.
 */
@Composable
private fun DownloadsRow(count: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(stringResource(MR.strings.label_download_queue)) },
        supportingContent = {
            Text(pluralStringResource(AYMR.plurals.home_downloads_queued, count, count))
        },
    )
}

/**
 * A section title, optionally with one action on the far side.
 *
 * The action is text rather than a chevron: *See all* says where it goes, and a chevron on a
 * section header says only that something happens.
 */
@Composable
private fun SectionHeader(
    text: String,
    action: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (action != null && onActionClick != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onActionClick),
            )
        }
    }
}

/**
 * A cover with its title written on it, and a progress bar flush to the bottom edge.
 *
 * The text used to sit in a stack underneath, which cost forty vertical points per card and made
 * three cards impossible at any cover size worth looking at. On the cover, over an ink scrim, it
 * costs nothing — and the scrim is where the brand's ink texture belongs anyway.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(
    item: ContinueItem,
    onClick: () -> Unit,
    onHide: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(ContinueCardWidth)
            .clip(MaterialTheme.shapes.medium)
            // combinedClickable rather than tvClickable: the hide action is a long press, the
            // same trade the library grid already made for its quick sheet.
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        ItemCover.Book(
            data = item.coverData,
            contentDescription = item.title,
        )

        // Bottom-anchored ink, so the type has something to sit on whatever the cover happens to
        // be. A fixed scrim over the whole card would dim the artwork for no reason.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC08080C)),
                    ),
                )
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                ),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
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
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            // One action, said with its consequence: the entry leaves this rail and nothing else.
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.home_hide_from_continue)) },
                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onHide()
                },
            )
        }
    }
}

/**
 * A title with an episode still to come, and how long the wait is.
 *
 * No click target. Opening the entry would show the episodes that already exist, which is not what
 * somebody looking at a countdown is asking about, and there is nothing else to open — the episode
 * does not exist yet on the source or anywhere else. So it reads and does not pretend to act.
 *
 * The caption is relative — *in 2 days*, *in 5 hours* — rather than a date and a clock time. A date
 * has to be converted against today before it means anything, and the only thing being asked here
 * is how long.
 */
@Composable
private fun AiringCard(item: AiringItem) {
    Box(
        modifier = Modifier
            .width(ContinueCardWidth)
            .clip(MaterialTheme.shapes.medium),
    ) {
        ItemCover.Book(data = item.coverUrl, contentDescription = item.title)

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC08080C))))
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                ),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    AYMR.strings.home_airing_episode_in,
                    item.episode,
                    relativeTimeText(item.airingAtMillis),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

/**
 * *2d*, *5h*, *20m* — how far off, in the largest unit that still says something.
 *
 * Deliberately not `relativeDateText`, which rounds to whole days: half of what this rail shows
 * airs today, and "today" is the answer it is least useful to give.
 */
@Composable
private fun relativeTimeText(atMillis: Long): String {
    val minutes = ((atMillis - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes >= MINUTES_PER_DAY -> stringResource(AYMR.strings.home_airing_days, minutes / MINUTES_PER_DAY)
        minutes >= MINUTES_PER_HOUR -> stringResource(AYMR.strings.home_airing_hours, minutes / MINUTES_PER_HOUR)
        else -> stringResource(AYMR.strings.home_airing_minutes, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 60L * 24L

/**
 * One thing that arrived, as a row.
 *
 * The words are the medium's — *Chapter* or *Episode* comes with the item name from the source —
 * because by this point you are looking at one specific thing rather than at a mixed shelf.
 */
@Composable
private fun UpdateRow(
    item: UpdateItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemCover.Square(
            data = item.coverData,
            contentDescription = item.title,
            modifier = Modifier.size(UpdateThumbSize),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.isNew) {
            NewPill()
        }
    }
}

/** No library at all: one sentence and the one button that fixes it. */
@Composable
private fun EmptyShelf(onDiscover: () -> Unit) {
    AnimatoEmptyState(
        message = stringResource(AYMR.strings.home_empty_shelf),
        actionLabel = stringResource(AYMR.strings.label_discover),
        onAction = onDiscover,
    )
}

/** Chapter and episode numbers are stored as doubles; whole ones should not read as `12.0`. */
private fun formatItemNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

private val ContinueCardWidth = 148.dp
private val UpdateThumbSize = 48.dp
