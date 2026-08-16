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
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import animato.ui.components.Pill
import animato.ui.entries.ItemCover
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
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
        var showAbout by rememberSaveable { mutableStateOf(false) }

        val open: (EntryItem) -> Unit = { item ->
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
                        onTracking = {
                            navigator.push(
                                when (contentType) {
                                    ContentType.MANGA -> MangaScreen(entryId)
                                    ContentType.ANIME -> AnimeScreen(entryId)
                                },
                            )
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
                    Text(
                        text = stringResource(
                            when (contentType) {
                                ContentType.MANGA -> AYMR.strings.entry_chapters_count
                                ContentType.ANIME -> AYMR.strings.entry_episodes_count
                            },
                            state.items.size.toString(),
                        ),
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
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
    onTracking: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One primary, and it names what it will open. "Read" with no number is a button you have to
        // press to find out what it does.
        Button(onClick = onResume, enabled = state.nextItem != null, modifier = Modifier.weight(1f)) {
            Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
            Text(
                text = state.nextItem?.let { next ->
                    stringResource(
                        if (state.hasStarted) AYMR.strings.entry_resume else AYMR.strings.entry_start,
                        formatNumber(next.number),
                    )
                } ?: stringResource(AYMR.strings.quick_nothing_left),
                modifier = Modifier.padding(start = MaterialTheme.padding.extraSmall),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        IconButton(onClick = onTracking) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = stringResource(MR.strings.manga_tracking_tab),
                tint = if (state.trackerCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
