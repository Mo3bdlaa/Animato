package animato.app.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.entry.EntryScreen
import animato.app.navigation.LensButton
import animato.domain.content.ContentType
import animato.ui.components.NewPill
import animato.ui.entries.ItemCover
import animato.ui.tv.tvClickable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The feed, both halves, grouped by day.
 *
 * Updates was Mihon's list or the anime list depending on the lens, which meant a day on which one
 * chapter and one episode arrived was two screens holding one row each. *What happened yesterday*
 * is a single question and this is the screen that answers it.
 *
 * The row is the same object as Home's *Latest updates* — 48 dp thumb, title, the item's own name,
 * a time and then either the NEW pill or a download — because they are literally the same thing at
 * two lengths. Day headers are deliberately small and muted: a date divides, it does not announce.
 */
@Composable
internal fun UpdatesContent() {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenModel = viewModel { UpdatesScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val refreshingMessage = stringResource(MR.strings.updating_library)
    val alreadyRunning = stringResource(MR.strings.update_already_running)
    val refresh: () -> Unit = {
        val started = screenModel.refresh()
        scope.launch {
            snackbarHostState.showSnackbar(if (started) refreshingMessage else alreadyRunning)
        }
    }

    val open: (UpdateItem) -> Unit = { item ->
        when (item.contentType) {
            // The reader and the player are activities in their own right, so this is an intent
            // rather than a navigator push — the same call both halves' own feeds made.
            ContentType.MANGA ->
                context.startActivity(ReaderActivity.newIntent(context, item.entryId, item.itemId))
            ContentType.ANIME -> scope.launch {
                animato.anime.player.PlayerLauncher.startPlayerActivity(
                    context = context,
                    animeId = item.entryId,
                    episodeId = item.itemId,
                    extPlayer = false,
                    sourceId = item.sourceId,
                )
            }
        }
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = { AppBarTitle(stringResource(MR.strings.label_recent_updates)) },
                scrollBehavior = scrollBehavior,
                actions = {
                    LensButton()
                    IconButton(onClick = refresh) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(MR.strings.action_update_library),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))

            state.isEmpty -> NothingNew(
                modifier = Modifier.padding(contentPadding),
                onCheckNow = refresh,
            )

            else -> LazyColumn(
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
            ) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}", contentType = "day") {
                        DayHeader(day.date)
                    }
                    items(
                        count = day.items.size,
                        key = { index ->
                            val item = day.items[index]
                            "${item.contentType}-${item.itemId}"
                        },
                        contentType = { "update" },
                    ) { index ->
                        val item = day.items[index]
                        UpdateRow(
                            item = item,
                            onClick = { open(item) },
                            onCoverClick = {
                                navigator.push(EntryScreen(item.entryId, item.contentType))
                            },
                            onDownload = { screenModel.download(item) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A date, at divider weight.
 *
 * Today and Yesterday by name because that is how anyone reads a feed's top; anything older gets
 * the locale's own medium date, which is the only format that is correct in every language the app
 * ships in.
 */
@Composable
private fun DayHeader(date: LocalDate) {
    val today = remember { LocalDate.now() }
    val label = when (date) {
        today -> stringResource(MR.strings.relative_time_today)
        today.minusDays(1) -> stringResource(AYMR.strings.relative_time_yesterday)
        else -> remember(date) { date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
    }

    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

/**
 * One arrival.
 *
 * Tapping the row opens the chapter or episode; tapping the cover opens the title it belongs to.
 * Those are two different intentions and a feed that only offers the first makes the second a
 * three-step detour.
 */
@Composable
private fun UpdateRow(
    item: UpdateItem,
    onClick: () -> Unit,
    onCoverClick: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        ItemCover.Square(
            data = item.coverData,
            contentDescription = item.title,
            modifier = Modifier.size(ThumbSize),
            shape = RoundedCornerShape(ThumbRadius),
            onClick = onCoverClick,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
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
        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = stringResource(MR.strings.manga_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Nothing arrived, with something to do about it.
 *
 * This replaces a kaomoji. An empty feed is the most common state a well-caught-up person sees, so
 * it has to say what it means — nothing since your last check, not "no updates" — and offer the one
 * action that could change it.
 */
@Composable
private fun NothingNew(
    modifier: Modifier = Modifier,
    onCheckNow: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Box(modifier = Modifier.height(EmptyTopSpace))
        Text(
            text = stringResource(AYMR.strings.updates_nothing_new),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCheckNow) {
            Text(stringResource(AYMR.strings.action_check_now))
        }
    }
}

private val ThumbSize = 48.dp
private val ThumbRadius = 12.dp
private val EmptyTopSpace = 96.dp
