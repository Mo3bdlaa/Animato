package animato.app.stremio

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.iptv.M3uPlaylist
import animato.anime.iptv.M3uPlaylistStore
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
    private val playlistStore: M3uPlaylistStore = Injekt.get(),
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

    /**
     * Whether addons that describe themselves as adult are offered.
     *
     * The same setting that hides NSFW extensions — one answer to one question, asked once in
     * Settings, rather than a second switch on a screen about something else.
     *
     * Read here and applied by the screen rather than filtered out of the list, because the list
     * is also what tells an *installed* addon that it is adult, and an addon does not stop being
     * one when the setting is turned off. Filtering here would have unmarked exactly the row that
     * most needs marking.
     */
    val showAdult: Boolean = sourcePreferences.showNsfwSource.get()

    init {
        viewModelScope.launchIO { _directoryAddons.value = directory.listed() }
    }

    /**
     * The M3U playlists, which live beside the addons rather than under them.
     *
     * Only the live-TV door shows these. A playlist is a different kind of thing — a file of
     * channels rather than a service that answers questions — and listing it under *Stremio addons*
     * would be filing it by the screen it happens to share rather than by what it is.
     */
    val playlists: StateFlow<List<M3uPlaylist>> = playlistStore.playlists

    /**
     * Add a playlist by address.
     *
     * Shares [installState] with the addon field on purpose: one screen, one thing being added at
     * a time, one place the outcome is reported. The two paths cannot run at once because there is
     * one dialog.
     */
    fun addPlaylist(url: String) {
        if (_installState.value is AddonInstallState.Working) return
        _installState.value = AddonInstallState.Working
        viewModelScope.launchIO {
            _installState.value = playlistStore.add(url).fold(
                onSuccess = { AddonInstallState.Added(it.name) },
                onFailure = {
                    AddonInstallState.Failed(it.message.orEmpty().ifBlank { UNREADABLE_PLAYLIST })
                },
            )
        }
    }

    fun removePlaylist(url: String) {
        playlistStore.remove(url)
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

/**
 * What to say when a playlist would not load and the failure carried no message of its own.
 *
 * Every other failure here already has a sentence — the store writes them — and this covers the
 * network exceptions that arrive with nothing readable in them.
 */
private const val UNREADABLE_PLAYLIST = "That address could not be read"
