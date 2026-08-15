package eu.kanade.tachiyomi.ui.browse.anime.migration.sources

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aniyomi.domain.source.interactor.SetAnimeMigrateSorting
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.model.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MigrateAnimeSourceScreenModel(
    preferences: AnimeSourcePreferences = Injekt.get(),
    private val getSourcesWithFavoriteCount: GetAnimeSourcesWithFavoriteCount = Injekt.get(),
    private val setMigrateSorting: SetAnimeMigrateSorting = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<MigrateAnimeSourceScreenModel.State>
        field = MutableStateFlow<MigrateAnimeSourceScreenModel.State>(State())

    private val _channel = Channel<Event>(Int.MAX_VALUE)
    val channel = _channel.receiveAsFlow()

    init {
        viewModelScope.launchIO {
            getSourcesWithFavoriteCount.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _channel.send(Event.FailedFetchingSourcesWithCount)
                }
                .collectLatest { sources ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            items = sources.toImmutableList(),
                        )
                    }
                }
        }

        preferences.migrationSortingDirection.changes()
            .onEach { state.update { state -> state.copy(sortingDirection = it) } }
            .launchIn(viewModelScope)

        preferences.migrationSortingMode.changes()
            .onEach { state.update { state -> state.copy(sortingMode = it) } }
            .launchIn(viewModelScope)
    }

    fun toggleSortingMode() {
        with(state.value) {
            val newMode = when (sortingMode) {
                SetAnimeMigrateSorting.Mode.ALPHABETICAL -> SetAnimeMigrateSorting.Mode.TOTAL
                SetAnimeMigrateSorting.Mode.TOTAL -> SetAnimeMigrateSorting.Mode.ALPHABETICAL
            }

            setMigrateSorting.await(newMode, sortingDirection)
        }
    }

    fun toggleSortingDirection() {
        with(state.value) {
            val newDirection = when (sortingDirection) {
                SetAnimeMigrateSorting.Direction.ASCENDING -> SetAnimeMigrateSorting.Direction.DESCENDING
                SetAnimeMigrateSorting.Direction.DESCENDING -> SetAnimeMigrateSorting.Direction.ASCENDING
            }

            setMigrateSorting.await(sortingMode, newDirection)
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: ImmutableList<Pair<AnimeSource, Long>> = persistentListOf(),
        val sortingMode: SetAnimeMigrateSorting.Mode = SetAnimeMigrateSorting.Mode.ALPHABETICAL,
        val sortingDirection: SetAnimeMigrateSorting.Direction = SetAnimeMigrateSorting.Direction.ASCENDING,
    ) {
        val isEmpty = items.isEmpty()
    }

    sealed interface Event {
        data object FailedFetchingSourcesWithCount : Event
    }
}
