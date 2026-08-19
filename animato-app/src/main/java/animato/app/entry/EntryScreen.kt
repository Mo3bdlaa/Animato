package animato.app.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.player.PlayerLauncher
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.components.Pill
import animato.ui.entries.ItemCover
import animato.ui.theme.LocalAnimatoPalette
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.NavigatorAdaptiveSheet
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.migration.search.MigrateSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * The title page, the same page for both halves.
 *
 * Tapping two covers that sat side by side in the same library grid used to land on two unrelated
 * designs — Mihon's screen for the manga, the Aniyomi port for the anime. This is the one page, and
 * the only thing that differs between a chapter row and an episode row is the word in the caption.
 *
 * ## What it owns and what it hands off
 *
 * It owns the header, the resume-aware primary, the heart, and the item list. It does not own the
 * deep tools either half has grown — scanlator filters, chapter-settings, seasons, notes, migration,
 * editing a cover — which are thousands of lines of working code that have nothing to do with how
 * this page looks. *All options* in the overflow opens the original screen, where they all still
 * are. That is a deliberate seam and not an omission: see EntryScreenModel.
 */
class EntryScreen(
    private val entryId: Long,
    private val contentType: ContentType,
    /**
     * Whether this was opened from a source rather than from the library.
     *
     * Carried for one reason: leaving incognito mode pops source-opened pages back to the root, and
     * that rule was written against Mihon's own screen. A page that replaced it without the flag
     * would quietly stop being popped.
     */
    val fromSource: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = viewModel(key = "entry-$contentType-$entryId") {
            EntryScreenModel(entryId = entryId, contentType = contentType)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var menuOpen by remember { mutableStateOf(false) }
        var editOpen by remember { mutableStateOf(false) }
        var trackingOpen by remember { mutableStateOf(false) }

        if (editOpen) {
            EditEntryDialog(
                state = state,
                onSave = screenModel::saveOverride,
                onDismiss = { editOpen = false },
            )
        }

        /*
         * Tracking, on this page rather than on the way to another one.
         *
         * *Tracking* in the overflow used to push the original title screen, which opens its own
         * tracking sheet only if you press its own tracking icon — so the menu item that said
         * tracking delivered a different page and one more press. The sheet is a Voyager screen in
         * its own right; showing it here is what the original screen does, minus the detour.
         *
         * Two of them, because the two halves each have their own — the one thing on this page
         * that could not be unified, since a track row belongs to one library or the other.
         */
        if (trackingOpen && !state.isLoading) {
            val dismiss = { trackingOpen = false }
            when (contentType) {
                ContentType.MANGA -> NavigatorAdaptiveSheet(
                    screen = TrackInfoDialogHomeScreen(
                        mangaId = state.entryId,
                        mangaTitle = state.title,
                        sourceId = state.sourceId,
                    ),
                    enableSwipeDismiss = { it.lastItem is TrackInfoDialogHomeScreen },
                    onDismissRequest = dismiss,
                )
                ContentType.ANIME -> NavigatorAdaptiveSheet(
                    screen = AnimeTrackInfoDialogHomeScreen(
                        animeId = state.entryId,
                        animeTitle = state.title,
                        sourceId = state.sourceId,
                    ),
                    enableSwipeDismiss = { it.lastItem is AnimeTrackInfoDialogHomeScreen },
                    onDismissRequest = dismiss,
                )
            }
        }
        var showAbout by rememberSaveable { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        // A refresh that found nothing and a refresh that failed both leave the page exactly as it
        // was, so the outcome has to be said out loud or the button reads as broken.
        val foundMessage = pluralStringResource(
            when (contentType) {
                ContentType.MANGA -> MR.plurals.notification_chapters_generic
                ContentType.ANIME -> AYMR.plurals.notification_episodes_generic
            },
            (state.refreshResult as? RefreshResult.Found)?.count ?: 0,
            (state.refreshResult as? RefreshResult.Found)?.count ?: 0,
        )
        val upToDateMessage = stringResource(AYMR.strings.entry_up_to_date)
        val failedMessage = stringResource(AYMR.strings.entry_refresh_failed)
        LaunchedEffect(state.refreshResult) {
            when (val result = state.refreshResult) {
                null -> return@LaunchedEffect
                is RefreshResult.Found -> snackbarHostState.showSnackbar(foundMessage)
                RefreshResult.UpToDate -> snackbarHostState.showSnackbar(upToDateMessage)
                // The source's own words when it has any — a 403 and a timeout are different facts
                // and only the source knows which happened.
                is RefreshResult.Failed -> snackbarHostState.showSnackbar(
                    result.message ?: failedMessage,
                )
            }
            screenModel.refreshResultShown()
        }

        val open: (EntryItem) -> Unit = { item ->
            // A season is another entry, not something to play. Opening it here rather than
            // branching inside the player keeps the player's contract simple: everything it is
            // handed is a thing with a video.
            if (item.isSeason) {
                navigator.push(EntryScreen(item.id, ContentType.ANIME))
            } else {
                when (contentType) {
                    ContentType.MANGA ->
                        context.startActivity(ReaderActivity.newIntent(context, entryId, item.id))
                    ContentType.ANIME -> scope.launch {
                        PlayerLauncher.startPlayerActivity(
                            context = context,
                            animeId = entryId,
                            episodeId = item.id,
                            extPlayer = false,
                        )
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = navigator::pop) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(MR.strings.action_bar_up_description),
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(
                                    MR.strings.action_menu_overflow_description,
                                ),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                // First in the menu because it acts on what is on screen, where
                                // both entries below leave for another screen entirely.
                                text = { Text(stringResource(AYMR.strings.action_edit_details)) },
                                trailingIcon = {
                                    if (state.override != null) {
                                        Text(
                                            text = stringResource(AYMR.strings.edit_details_edited),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    editOpen = true
                                },
                            )
                            DropdownMenuItem(
                                // Here rather than as an icon in the header, where it had no label
                                // and a glyph that read as refresh. Binding *this* title to a
                                // tracker is the original screen's dialog, opened over this page;
                                // the hub in Settings is about accounts rather than titles.
                                text = { Text(stringResource(AYMR.strings.entry_tracking)) },
                                trailingIcon = {
                                    if (state.trackerCount > 0) {
                                        Text(
                                            text = state.trackerCount.toString(),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    trackingOpen = true
                                },
                            )
                            /*
                             * Moving a title to another source, which is the thing you need on the
                             * day a source dies.
                             *
                             * Both halves already have the whole flow — search the other sources,
                             * pick the match, choose what carries over — and it was reachable only
                             * from the original screens, so a library built on this page had no
                             * answer at all for an extension that stopped working. This is the
                             * entrance, not a reimplementation.
                             *
                             * Only for a title in the library. Migrating something you have not
                             * saved is moving nothing from nowhere.
                             */
                            if (state.inLibrary) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.action_migrate)) },
                                    onClick = {
                                        menuOpen = false
                                        navigator.push(
                                            when (contentType) {
                                                ContentType.MANGA -> MigrateSearchScreen(entryId)
                                                ContentType.ANIME -> MigrateAnimeSearchScreen(entryId)
                                            },
                                        )
                                    },
                                )
                            }
                            DropdownMenuItem(
                                // Everything this page deliberately does not re-implement is one tap
                                // away and unchanged.
                                text = { Text(stringResource(AYMR.strings.entry_all_options)) },
                                onClick = {
                                    menuOpen = false
                                    navigator.push(
                                        when (contentType) {
                                            ContentType.MANGA -> MangaScreen(entryId)
                                            ContentType.ANIME -> AnimeScreen(entryId)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            if (state.isLoading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }

            LazyColumn(
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.large),
            ) {
                item(key = "header") {
                    EntryHeader(
                        state = state,
                        onToggleLibrary = screenModel::toggleInLibrary,
                        onResume = { state.nextItem?.let(open) },
                        onRefresh = screenModel::refresh,
                        onOpenInBrowser = {
                            state.webViewUrl?.let { url ->
                                context.startActivity(
                                    WebViewActivity.newIntent(context, url, sourceId = null, title = state.title),
                                )
                            }
                        },
                    )
                }

                item(key = "about") {
                    AboutSection(
                        state = state,
                        expanded = showAbout,
                        onToggle = { showAbout = !showAbout },
                    )
                }

                item(key = "items-header") {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    ) {
                        Text(
                            text = stringResource(
                                when (contentType) {
                                    ContentType.MANGA -> AYMR.strings.entry_chapters_count
                                    ContentType.ANIME -> AYMR.strings.entry_episodes_count
                                },
                                state.items.size.toString(),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        /*
                         * How often this thing arrives, and when the next one is due.
                         *
                         * Aniyomi's screen spends one of four action buttons on an hourglass
                         * reading "5 days", or "N/A" when it has nothing — a control that mostly
                         * says nothing, and never says what the number is *of*. A device asked
                         * for the fact and not the button: *"add the bit about roughly how often
                         * an episode drops."* So it is a sentence here, under the count it is
                         * about, and it is simply absent when there is no prediction — which is
                         * every entry outside the library, since the update job is what computes
                         * it, and every completed work, which has no next one by definition.
                         */
                        val cadence = state.releaseIntervalDays?.let { days ->
                            stringResource(
                                when (contentType) {
                                    ContentType.MANGA -> AYMR.strings.entry_release_every_chapters
                                    ContentType.ANIME -> AYMR.strings.entry_release_every_episodes
                                },
                                pluralStringResource(MR.plurals.day, days, days),
                            )
                        }
                        val next = state.nextReleaseDays?.let { days ->
                            if (days == 0) {
                                stringResource(AYMR.strings.entry_release_next_soon)
                            } else {
                                stringResource(
                                    AYMR.strings.entry_release_next_in,
                                    pluralStringResource(MR.plurals.day, days, days),
                                )
                            }
                        }
                        listOfNotNull(cadence, next).takeIf { it.isNotEmpty() }?.let { parts ->
                            Text(
                                text = parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // A gap in the numbering, said out loud. A list that jumps from 40 to 71
                        // without comment reads as a list somebody has already read the middle of.
                        if (state.missingCount > 0) {
                            Text(
                                text = pluralStringResource(
                                    when (contentType) {
                                        ContentType.MANGA -> MR.plurals.missing_chapters
                                        ContentType.ANIME -> AYMR.plurals.missing_items
                                    },
                                    state.missingCount,
                                    state.missingCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalAnimatoPalette.current.warning,
                            )
                        }
                    }
                }

                // A source that lists nothing left the page as a header over black. It is a real
                // state — a title page opened straight from a search, before anything is known
                // about it — and the refresh in the header is the one thing that could change it.
                if (state.items.isEmpty()) {
                    item(key = "no-items") {
                        AnimatoEmptyState(
                            message = stringResource(AYMR.strings.entry_no_items),
                            actionLabel = stringResource(MR.strings.action_webview_refresh),
                            onAction = screenModel::refresh,
                        )
                    }
                }

                items(items = state.items, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        contentType = contentType,
                        onClick = { open(item) },
                        onToggleViewed = { screenModel.setViewed(item, !item.viewed) },
                        onToggleBookmark = { screenModel.toggleBookmark(item) },
                    )
                }
            }
        }
    }
}

/**
 * The backdrop: the same artwork twice.
 *
 * Blurred and darkened behind the cover rather than a second image fetched from somewhere — there
 * is no second image, and asking a source for one would make this page slower on exactly the
 * connection where it is already slowest. The gradient is what carries the text, so the header
 * reads whether the artwork behind it is black or white.
 */
@Composable
private fun EntryHeader(
    state: EntryState,
    onToggleLibrary: () -> Unit,
    onResume: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().height(BackdropHeight)) {
        AsyncImage(
            model = state.coverData,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(BackdropBlur),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x9908080C), Color(0xFF08080C)),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.Bottom,
        ) {
            ItemCover.Book(
                data = state.coverData,
                contentDescription = state.title,
                modifier = Modifier.width(CoverWidth),
                shape = RoundedCornerShape(CoverRadius),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                state.author?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = SUBDUED_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(
                        text = stringResource(
                            when (state.contentType) {
                                ContentType.MANGA -> AYMR.strings.label_manga
                                ContentType.ANIME -> AYMR.strings.label_anime
                            },
                        ),
                        containerColor = Color.White.copy(alpha = PILL_ALPHA),
                        contentColor = Color.White,
                    )
                    state.statusLabel?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = SUBDUED_ALPHA),
                        )
                    }
                    Text(
                        text = state.sourceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = SUBDUED_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    /*
     * One row: the primary takes what is left after the icons take their 48 dp each.
     *
     * It has been both ways now. The row was split because the full sentence "Nothing left — you
     * are caught up" truncated beside the icons; then a device asked for the opposite — *"the
     * play button is too big, make it smaller so refresh, webview and favorite can sit next to
     * it"* — and the caught-up sentence was the only label that ever needed the width. So the row
     * is shared again and that one label got shorter instead, which is the fix the truncation
     * actually wanted.
     */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The primary names what it will open. "Read" with no number is a button you have to
        // press to find out what it does.
        Button(
            onClick = onResume,
            enabled = state.nextItem != null,
            modifier = Modifier.weight(1f),
        ) {
            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
            Text(
                text = state.nextItem?.let { next ->
                    stringResource(
                        if (state.hasStarted) AYMR.strings.entry_resume else AYMR.strings.entry_start,
                        formatNumber(next.number),
                    )
                } ?: stringResource(AYMR.strings.entry_caught_up),
                modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The heart means "in your library". The word favourite never appears anywhere in the app.
            IconButton(onClick = onToggleLibrary) {
                Icon(
                    imageVector = if (state.inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(MR.strings.add_to_library),
                    tint = if (state.inLibrary) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            /*
             * The site itself, next to the refresh that asks it politely.
             *
             * Asked for from a device: "we need a webview button beside reload, because when something
             * is broken — which is often — opening it in the browser helps." That is the honest reason.
             * A source can be rate limiting, behind Cloudflare, or serving a page the extension no
             * longer parses, and in all three the site still works; being able to look is the
             * difference between a dead entry and a readable one.
             *
             * Absent rather than disabled when there is no page — a local entry, or a source whose
             * extension has been removed and is now a stub.
             */
            if (state.webViewUrl != null) {
                IconButton(onClick = onOpenInBrowser) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = stringResource(MR.strings.action_open_in_web_view),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            /*
             * Circular arrows mean refresh, and for one release they opened the original screen.
             *
             * From a device: "when I press the tracker icon, expecting it to check whether there is
             * anything new, it opens the old page instead." The glyph was telling the truth about what
             * it looked like and a lie about what it did. So the glyph kept its promise and tracking —
             * which had no label to say it was tracking — moved into the overflow, where it has a word.
             */
            IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(RefreshSpinnerSize),
                        strokeWidth = RefreshSpinnerStroke,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(MR.strings.action_webview_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The description and the genres, collapsed by default.
 *
 * The reason anybody opens this page is the next unread item, so the synopsis does not get to push
 * the list below the fold. Tapping it opens it.
 */
@Composable
private fun AboutSection(
    state: EntryState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        state.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.genres.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                state.genres.forEach { genre ->
                    AssistChip(onClick = onToggle, label = { Text(genre) })
                }
            }
        }
    }
}

/**
 * One chapter or episode.
 *
 * Read state is the muted text rather than a tick, because a list where half the rows carry an icon
 * and half do not reads as two lists. The bookmark is the only trailing control: downloading from
 * here is the overflow's job, and a per-row download button on a thousand-row list is a thousand
 * buttons nobody presses.
 */
@Composable
private fun ItemRow(
    item: EntryItem,
    contentType: ContentType,
    onClick: () -> Unit,
    onToggleViewed: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val alpha = if (item.viewed) VIEWED_ALPHA else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Box(
            modifier = Modifier
                .size(NumberBoxSize)
                .clip(RoundedCornerShape(NumberBoxRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onToggleViewed),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = formatNumber(item.number),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    // When it was published, which the old screen showed and this one dropped. It
                    // is the only thing on the row that says whether a source has stopped updating.
                    item.dateUpload.takeIf { it > 0 }?.let { relativeDateText(it) },
                    item.scanlator?.takeIf { it.isNotBlank() },
                    stringResource(
                        when (contentType) {
                            ContentType.MANGA -> AYMR.strings.caption_chapters
                            ContentType.ANIME -> AYMR.strings.caption_episodes
                        },
                        formatNumber(item.number),
                    ),
                    stringResource(MR.strings.label_downloaded).takeIf { item.downloaded },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (item.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(MR.strings.action_bookmark),
                tint = if (item.bookmarked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Chapter and episode numbers are doubles; whole ones should not read as `12.0`. */
private fun formatNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

private val RefreshSpinnerSize = 20.dp
private val RefreshSpinnerStroke = 2.dp

private const val SUBDUED_ALPHA = 0.75f
private const val PILL_ALPHA = 0.18f
private const val VIEWED_ALPHA = 0.5f
private const val COLLAPSED_DESCRIPTION_LINES = 3
private val BackdropHeight = 260.dp
private val BackdropBlur = 24.dp
private val CoverWidth = 112.dp
private val CoverRadius = 12.dp
private val NumberBoxSize = 40.dp
private val NumberBoxRadius = 8.dp
