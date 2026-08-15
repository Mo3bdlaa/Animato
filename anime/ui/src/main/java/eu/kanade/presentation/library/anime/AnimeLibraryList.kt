package eu.kanade.presentation.library.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import animato.ui.library.DownloadsBadge
import animato.ui.library.EntryListItem
import animato.ui.library.GlobalSearchItem
import animato.ui.library.LanguageBadge
import animato.ui.library.UnviewedBadge
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun AnimeLibraryList(
    items: List<AnimeLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            contentType = { "anime_library_list_item" },
        ) { libraryItem ->
            val anime = libraryItem.libraryAnime.anime
            EntryListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAnime.id },
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unseenCount)
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryAnime) },
                onClick = { onClick(libraryItem.libraryAnime) },
                onClickContinueViewing = if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
                    { onClickContinueWatching(libraryItem.libraryAnime) }
                } else {
                    null
                },
                entries = entries,
                containerHeight = containerHeight,
            )
        }
    }
}
