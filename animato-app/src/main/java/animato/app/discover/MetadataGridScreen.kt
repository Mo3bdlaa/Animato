package animato.app.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.search.AnimatoSearchScreen
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.entries.ItemCover
import animato.ui.tv.tvClickable
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

private val GridItemMinWidth = 104.dp
private val GridCoverRadius = 12.dp

/**
 * One rail, at full depth: the grid behind *view all*.
 *
 * This is the "browse-a-rail screen" Discover's header comment promised and did not have — the
 * reason the rails had no *see all* at first was precisely that this screen did not exist. It
 * also carries the questions Discover no longer lists: the front page slimmed to trending after a
 * device found five rails a lot, and top-rated survives here rather than nowhere.
 *
 * Tapping a title searches for it, the same as tapping it on the rail: a metadata title has no
 * source, so a search across the installed sources of its own medium is the only honest open.
 */
class MetadataGridScreen(
    private val rail: MetadataRail,
    private val contentType: ContentType,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = viewModel(key = "metadata-grid-${rail.name}-${contentType.name}") {
            MetadataGridScreenModel(rail, contentType)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = "${stringResource(rail.labelRes())} · ${stringResource(contentType.labelRes())}",
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> AnimatoEmptyState(
                    message = stringResource(AYMR.strings.discover_rail_empty),
                    modifier = Modifier.padding(contentPadding),
                    actionLabel = stringResource(MR.strings.action_retry),
                    onAction = screenModel::retry,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = GridItemMinWidth),
                    contentPadding = contentPadding + PaddingValues(MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(items = state.items, key = { it.key }) { item ->
                        Column(
                            modifier = Modifier.tvClickable {
                                navigator.push(AnimatoSearchScreen(item.title, contentType))
                            },
                        ) {
                            ItemCover.Book(
                                modifier = Modifier.fillMaxWidth(),
                                data = item.coverUrl,
                                contentDescription = item.title,
                                shape = RoundedCornerShape(GridCoverRadius),
                            )
                            Text(
                                text = item.title,
                                modifier = Modifier.padding(top = MaterialTheme.padding.extraSmall),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.caption?.let { caption ->
                                Text(
                                    text = caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
