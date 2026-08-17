package animato.app.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Immutable
data class MetadataGridState(
    val isLoading: Boolean = true,
    val items: List<MetadataItem> = emptyList(),
)

/**
 * The grid's model: one fetch, deeper than the rail's.
 *
 * The rail shows a couple of dozen because it is a shelf; the grid is the whole answer to the
 * question, so it asks for as much as AniList serves in one page. No pagination — fifty covers is
 * more than anyone browses to the end of, and a *load more* on a list of suggestions is homework.
 */
class MetadataGridScreenModel(
    private val rail: MetadataRail,
    private val contentType: ContentType,
    private val catalog: MetadataCatalog = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<MetadataGridState>
        field = MutableStateFlow(MetadataGridState())

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val items = catalog.fetch(rail, contentType, limit = GRID_LIMIT)
            state.update { MetadataGridState(isLoading = false, items = items) }
        }
    }

    private companion object {
        const val GRID_LIMIT = 50
    }
}
