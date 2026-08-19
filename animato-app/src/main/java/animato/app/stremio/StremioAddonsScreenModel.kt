package animato.app.stremio

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.stremio.DirectoryAddon
import animato.anime.stremio.StremioAddon
import animato.anime.stremio.StremioAddonDirectory
import animato.anime.stremio.StremioAddonStore
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * What happened to the address someone just typed.
 *
 * Modelled as a state rather than a callback because adding an addon is a round trip that can take
 * seconds over a slow connection, and the two things the dialog owes the user during it — that
 * something is happening, and that the button is not going to work twice — both need a state to
 * read. [Added] exists so the dialog can close on success without having to guess.
 */
@Immutable
sealed interface AddonInstallState {
    data object Idle : AddonInstallState
    data object Working : AddonInstallState
    data class Added(val name: String) : AddonInstallState
    data class Failed(val message: String) : AddonInstallState
}

class StremioAddonsScreenModel(
    private val store: StremioAddonStore = Injekt.get(),
    private val directory: StremioAddonDirectory = StremioAddonDirectory(),
    sourcePreferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    val addons: StateFlow<List<StremioAddon>> = store.addons

    /**
     * The community list, fetched once and left alone.
     *
     * Empty until it arrives, and empty forever if it does not: the section simply is not drawn,
     * which is the right amount of noise for a directory nobody asked to load. It is not refreshed
     * with the rest of the screen either — pull-to-refresh here means *re-check my addons*, and
     * re-downloading ninety strangers' manifests is not that.
     */
    private val _directoryAddons = MutableStateFlow<List<DirectoryAddon>>(emptyList())
    val directoryAddons: StateFlow<List<DirectoryAddon>> = _directoryAddons.asStateFlow()

    init {
        viewModelScope.launchIO {
            // The same setting that hides NSFW extensions hides the addons that describe
            // themselves as adult — one answer to one question, asked once in Settings, rather
            // than a second switch on a screen about something else.
            val showAdult = sourcePreferences.showNsfwSource.get()
            _directoryAddons.value = directory.listed().filter { showAdult || !it.isAdult }
        }
    }

    private val _installState = MutableStateFlow<AddonInstallState>(AddonInstallState.Idle)
    val installState: StateFlow<AddonInstallState> = _installState.asStateFlow()

    fun install(url: String) {
        if (_installState.value is AddonInstallState.Working) return
        _installState.value = AddonInstallState.Working
        viewModelScope.launchIO {
            _installState.value = store.install(url).fold(
                onSuccess = { AddonInstallState.Added(it.manifest.name) },
                // The store has already turned every failure into a sentence; anything without
                // one would be a bug here rather than something the user did, so it is still
                // shown rather than swallowed.
                onFailure = { AddonInstallState.Failed(it.message.orEmpty()) },
            )
        }
    }

    fun remove(url: String) {
        store.remove(url)
    }

    /** Clear the last outcome so a reopened dialog does not start on an old error. */
    fun acknowledge() {
        _installState.value = AddonInstallState.Idle
    }

    fun refresh() {
        viewModelScope.launchIO { store.refresh() }
    }
}
