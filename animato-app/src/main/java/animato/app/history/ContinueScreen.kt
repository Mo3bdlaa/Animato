package animato.app.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.entry.EntryScreen
import animato.app.navigation.HomeScreenModel
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.entries.ItemCover
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

/**
 * Everything you have started, behind Home's *view all*.
 *
 * Home's Continue rail is a shelf — a screenful of the most recent, scrolled sideways. This is the
 * same list without the ceiling, in a grid, because past twenty items sideways scrolling stops
 * being browsing and becomes work.
 *
 * It reads [HomeScreenModel] rather than a model of its own: the list, the lens filtering and the
 * hide-from-Continue rule are all already there, and a second implementation of "what have I
 * started" is a second answer to that question waiting to disagree with the first.
 */
class ContinueScreen : Screen() {

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = viewModel { HomeScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val lens = animato.app.navigation.contentLens()
        val items = state.continueItems.filter { lens.accepts(it.contentType) }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.label_continue),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            if (items.isEmpty()) {
                AnimatoEmptyState(
                    message = stringResource(AYMR.strings.home_continue_empty),
                    modifier = Modifier.padding(contentPadding),
                )
                return@Scaffold
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = GridItemMinWidth),
                contentPadding = contentPadding + PaddingValues(MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(items = items, key = { it.railKey }) { item ->
                    var menuOpen by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            // The same long press the rail answers, for the same reason: this is
                            // the same list, and a gesture that works on one shelf and not on the
                            // screen behind it is a gesture nobody trusts.
                            .combinedClickable(
                                onClick = { navigator.push(EntryScreen(item.entryId, item.contentType)) },
                                onLongClick = { menuOpen = true },
                            ),
                    ) {
                        ItemCover.Book(
                            modifier = Modifier.fillMaxWidth(),
                            data = item.coverData,
                            contentDescription = item.title,
                        )
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
                            DropdownMenuItem(
                                text = { Text(stringResource(AYMR.strings.home_hide_from_continue)) },
                                leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    screenModel.hideFromContinue(item)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whole numbers lose the decimal; a half chapter keeps it. Shared with Home's own rail. */
private fun formatItemNumber(number: Double): String =
    if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()

private val GridItemMinWidth = 104.dp
