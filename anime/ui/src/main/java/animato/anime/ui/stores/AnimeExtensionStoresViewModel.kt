package animato.anime.ui.stores

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.extension.anime.interactor.AddAnimeExtensionStore
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import mihon.domain.extension.anime.interactor.RemoveAnimeExtensionStore
import mihon.domain.extension.anime.interactor.UpdateAnimeExtensionStores
import mihon.domain.extension.anime.model.AnimeExtensionStore
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AnimeExtensionStoresViewModel(
    private val getExtensionStores: GetAnimeExtensionStores = Injekt.get(),
    private val addExtensionStore: AddAnimeExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveAnimeExtensionStore = Injekt.get(),
    private val updateExtensionStores: UpdateAnimeExtensionStores = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
) : ViewModel() {

    private val dialog = MutableStateFlow<AnimeExtensionStoreDialog?>(null)

    val state: StateFlow<AnimeExtensionStoreScreenState> = combine(
        getExtensionStores.subscribe(),
        dialog,
    ) { stores, dialog ->
        AnimeExtensionStoreScreenState.Success(stores = stores, dialog = dialog)
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), AnimeExtensionStoreScreenState.Loading)

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        viewModelScope.launch {
            dialog.update {
                when (it) {
                    is AnimeExtensionStoreDialog.Create -> it.copy(processing = true)
                    is AnimeExtensionStoreDialog.Confirm -> it.copy(processing = true)
                    else -> it
                }
            }
            addExtensionStore(baseUrl)
                .onSuccess {
                    extensionManager.findAvailableExtensions()
                    dismissDialog()
                }
                .onFailure { throwable ->
                    dialog.update {
                        when (it) {
                            is AnimeExtensionStoreDialog.Create -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            is AnimeExtensionStoreDialog.Confirm -> it.copy(
                                processing = false,
                                errorMessage = throwable.message ?: "unknown error",
                            )
                            else -> it
                        }
                    }
                }
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        viewModelScope.launchIO {
            updateExtensionStores()
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        viewModelScope.launchIO {
            removeExtensionStore(baseUrl)
            extensionManager.findAvailableExtensions()
        }
    }

    fun addFromDeeplink(storeIndexUrl: String) {
        viewModelScope.launchIO {
            val alreadyExists = getExtensionStores.get().any { it.indexUrl == storeIndexUrl }
            dialog.update { AnimeExtensionStoreDialog.Confirm(url = storeIndexUrl, alreadyExists = alreadyExists) }
        }
    }

    fun showDialog(dialog: AnimeExtensionStoreDialog) {
        this.dialog.update { dialog }
    }

    fun dismissDialog() {
        dialog.update { null }
    }
}

sealed class AnimeExtensionStoreDialog {
    data class Create(val processing: Boolean = false, val errorMessage: String? = null) : AnimeExtensionStoreDialog()
    data class Delete(val store: AnimeExtensionStore) : AnimeExtensionStoreDialog()
    data class Confirm(
        val url: String,
        val alreadyExists: Boolean = false,
        val processing: Boolean = false,
        val errorMessage: String? = null,
    ) : AnimeExtensionStoreDialog()
}

sealed class AnimeExtensionStoreScreenState {

    @Immutable
    data object Loading : AnimeExtensionStoreScreenState()

    @Immutable
    data class Success(
        val stores: List<AnimeExtensionStore>,
        val dialog: AnimeExtensionStoreDialog? = null,
    ) : AnimeExtensionStoreScreenState() {

        val isEmpty: Boolean
            get() = stores.isEmpty()
    }
}
