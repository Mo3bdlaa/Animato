package animato.ui.storage

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.launchIO
import kotlin.random.Random

/**
 * The shared half of a storage screen: which entries have downloads, and how large they are.
 *
 * Generic over the library entry [T] because the two libraries store different things, and written
 * against [StorageCategory] rather than either library's category model — see that type for why.
 * A subclass supplies the flows and the accessors; all it has to implement is deleting.
 */
abstract class CommonStorageScreenModel<T>(
    private val downloadCacheChanges: SharedFlow<Unit>,
    private val downloadCacheIsInitializing: StateFlow<Boolean>,
    private val libraries: Flow<List<T>>,
    private val categories: Flow<List<StorageCategory>>,
    private val getDownloadSize: T.() -> Long,
    private val getDownloadCount: T.() -> Int,
    private val getId: T.() -> Long,
    private val getCategoryId: T.() -> Long,
    private val getTitle: T.() -> String,
    private val getThumbnail: T.() -> String?,
) : StateViewModel<StorageScreenState>(StorageScreenState.Loading) {

    private val selectedCategory = MutableStateFlow(AllCategory)

    init {
        viewModelScope.launchIO {
            combine(
                flow = downloadCacheChanges,
                flow2 = downloadCacheIsInitializing,
                flow3 = libraries,
                flow4 = categories,
                flow5 = selectedCategory,
                transform = { _, _, libraries, categories, selectedCategory ->
                    // initialize the screen with an empty state
                    mutableState.update {
                        StorageScreenState.Success(
                            selectedCategory = selectedCategory,
                            categories = listOf(AllCategory, *categories.toTypedArray()),
                            items = emptyList(),
                        )
                    }

                    val distinctLibraries = libraries.distinctBy {
                        it.getId()
                    }.filter { item ->
                        val categoryId = item.getCategoryId()
                        when {
                            // if all is selected, we want to make sure to include all entries
                            // from only visible categories
                            selectedCategory == AllCategory -> categories.find {
                                it.id == categoryId
                            } != null

                            // else include only entries from the selected category
                            else -> categoryId == selectedCategory.id
                        }
                    }

                    distinctLibraries.forEach { library ->
                        val random = Random(library.getId())
                        val item = StorageItem(
                            id = library.getId(),
                            title = library.getTitle(),
                            size = library.getDownloadSize(),
                            thumbnail = library.getThumbnail(),
                            entriesCount = library.getDownloadCount(),
                            color = Color(
                                random.nextInt(255),
                                random.nextInt(255),
                                random.nextInt(255),
                            ),
                        )

                        mutableState.update { state ->
                            when (state) {
                                is StorageScreenState.Success -> state.copy(
                                    items = (state.items + item).sortedByDescending { it.size },
                                )

                                else -> state
                            }
                        }
                    }
                },
            ).collectLatest {}
        }
    }

    fun setSelectedCategory(category: StorageCategory) {
        selectedCategory.update { category }
    }

    abstract fun deleteEntry(id: Long)

    companion object {
        /**
         * A pseudo-category used to display all entries irrespective of the category.
         */
        private val AllCategory = StorageCategory(id = StorageCategory.ALL_ID, name = "All")
    }
}
