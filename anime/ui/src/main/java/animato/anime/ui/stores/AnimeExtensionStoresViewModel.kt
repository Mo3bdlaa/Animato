package animato.anime.ui.stores

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.player.describeForUser
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
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class AnimeExtensionStoresViewModel(
    private val getExtensionStores: GetAnimeExtensionStores = Injekt.get(),
    private val addExtensionStore: AddAnimeExtensionStore = Injekt.get(),
    private val removeExtensionStore: RemoveAnimeExtensionStore = Injekt.get(),
    private val updateExtensionStores: UpdateAnimeExtensionStores = Injekt.get(),
    private val extensionManager: AnimeExtensionManager = Injekt.get(),
    private val context: Application = Injekt.get(),
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
        // A Stremio addon pasted here is not a mistake anybody should have to diagnose. The two
        // screens sit one row apart on Sources and both take a URL, so this is where people who
        // have only ever added a repository will try first — and the store fetch would answer
        // with a parse failure about an index nobody mentioned. Name what it is and where it goes.
        if (looksLikeStremioAddon(baseUrl)) {
            failDialog(context.stringResource(AYMR.strings.stremio_wrong_screen))
            return
        }
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
                    // The same translation the player uses, for the same reason: a repository that
                    // is simply unreachable said so as a bare class name, under a text field, to
                    // somebody who had just typed a URL and could have retried.
                    failDialog(
                        throwable.describeForUser()
                            ?: throwable.message
                            ?: context.stringResource(MR.strings.unknown_error),
                    )
                }
        }
    }

    private fun failDialog(message: String) {
        dialog.update {
            when (it) {
                is AnimeExtensionStoreDialog.Create -> it.copy(processing = false, errorMessage = message)
                is AnimeExtensionStoreDialog.Confirm -> it.copy(processing = false, errorMessage = message)
                else -> it
            }
        }
    }

    /**
     * Whether a URL is an addon rather than an extension store.
     *
     * By path, not by fetching it: an extension store index is `index.min.json`, and every Stremio
     * addon ends in `manifest.json` — that is the addon's whole address. A wrong guess here costs a
     * message pointing at the other screen, which is recoverable; fetching first to be certain
     * would make the common case slower to be no more right.
     */
    private fun looksLikeStremioAddon(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("stremio://", ignoreCase = true) ||
            trimmed.substringBefore('?').trimEnd('/').endsWith("/manifest.json", ignoreCase = true)
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
