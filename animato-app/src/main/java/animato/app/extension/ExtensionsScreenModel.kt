package animato.app.extension

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import mihon.domain.extension.interactor.GetExtensionStoreCountAsFlow
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Which half an extension belongs to, and the object the managers want back.
 *
 * The two halves have parallel-but-unrelated types — `Extension` and `AnimeExtension`, each with
 * its own manager — and nothing can make them one type without editing Mihon's files. So the screen
 * projects both onto [ExtensionRow] for drawing, and keeps the original here for acting on. Every
 * action in this model is a `when` over these two cases and nothing else.
 */
@Immutable
sealed interface ExtensionHandle {

    val contentType: ContentType

    @Immutable
    data class Manga(val extension: Extension) : ExtensionHandle {
        override val contentType = ContentType.MANGA
    }

    @Immutable
    data class Anime(val extension: AnimeExtension) : ExtensionHandle {
        override val contentType = ContentType.ANIME
    }
}

/** One row on the screen, whichever half it came from. */
@Immutable
data class ExtensionRow(
    val key: String,
    val name: String,
    val lang: String,
    val versionName: String,
    val isNsfw: Boolean,
    val hasUpdate: Boolean,
    val isObsolete: Boolean,
    val isUntrusted: Boolean,
    val isInstalled: Boolean,
    val installStep: InstallStep,
    val handle: ExtensionHandle,
) {
    val contentType: ContentType get() = handle.contentType
}

/** Available extensions, grouped by the language they serve. */
@Immutable
data class ExtensionLanguageGroup(
    val languageCode: String,
    val languageName: String,
    val rows: List<ExtensionRow>,
)

@Immutable
data class ExtensionsState(
    val isLoading: Boolean = true,
    val lens: ContentFilter = ContentFilter.ALL,
    val searchQuery: String? = null,
    val installed: List<ExtensionRow> = emptyList(),
    val available: List<ExtensionLanguageGroup> = emptyList(),
    val storeCount: Int = 0,
) {
    val hasUpdates: Boolean get() = installed.any { it.hasUpdate }
}

/**
 * Every extension, both halves, one screen.
 *
 * ## The problem this replaces
 *
 * Sources and extensions used to be Mihon's browse tabs for manga and a separate port of Aniyomi's
 * for anime, reached by flipping the lens and landing somewhere that looked different. From a
 * device: *"the extensions are absurd and there is no way to tell them apart in there."* That is
 * literally true — the manga list and the anime list were different screens, so nothing on either
 * had to say which it was.
 *
 * ## The type chip, and the one exception in the whole app
 *
 * Everywhere else, narrowing the lens *removes* the type mark, because when every row on screen is
 * the same kind the label is noise. Here the chip is on **every row regardless of the lens**. This
 * screen exists to tell anime and manga extensions apart; a mark that answers that question cannot
 * be a thing that sometimes disappears. The lens still decides which rows are listed.
 *
 * ## Why the lists are merged rather than concatenated
 *
 * Installed is one alphabetical list across both halves, and Available is grouped by language and
 * then alphabetical inside each group. Concatenating manga-then-anime would have rebuilt the split
 * in a single list, which is worse than two screens: it looks unified and is not.
 */
class ExtensionsScreenModel(
    getExtensions: GetExtensionsByType = Injekt.get(),
    getAnimeExtensions: GetAnimeExtensionsByType = Injekt.get(),
    getStoreCount: GetExtensionStoreCountAsFlow = Injekt.get(),
    getAnimeStoreCount: GetAnimeExtensionStoreCountAsFlow = Injekt.get(),
    contentPreferences: ContentPreferences = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<ExtensionsState>
        field = MutableStateFlow(ExtensionsState(lens = contentPreferences.contentFilter.get()))

    /**
     * What each package is doing right now, keyed by package name.
     *
     * Held outside the state so that a download step ticking over does not re-run the grouping and
     * sorting of every list on the screen.
     */
    private val currentSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())

    init {
        val context = Injekt.get<Application>()

        viewModelScope.launchIO {
            combine(
                getExtensions.subscribe(),
                getAnimeExtensions.subscribe(),
                currentSteps,
                contentPreferences.contentFilter.changes(),
                state.map { it.searchQuery }.distinctUntilChanged(),
            ) { manga, anime, steps, lens, query ->
                val installed = buildList {
                    if (lens.includesManga) {
                        // Updates are listed among the rest rather than in a pending section of
                        // their own. The Update pill on the row already says which ones, and a
                        // section that empties itself moves every row below it on each install.
                        addAll((manga.updates + manga.installed).map { it.toRow(steps) })
                        addAll(manga.untrusted.map { it.toRow(steps) })
                    }
                    if (lens.includesAnime) {
                        addAll((anime.updates + anime.installed).map { it.toRow(steps) })
                        addAll(anime.untrusted.map { it.toRow(steps) })
                    }
                }
                    .filter { it.matches(query) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val available = buildList {
                    if (lens.includesManga) addAll(manga.available.map { it.toRow(steps) })
                    if (lens.includesAnime) addAll(anime.available.map { it.toRow(steps) })
                }
                    .filter { it.matches(query) }
                    .groupBy { it.lang }
                    .map { (lang, rows) ->
                        ExtensionLanguageGroup(
                            languageCode = lang,
                            languageName = LocaleHelper.getSourceDisplayName(lang, context),
                            rows = rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
                        )
                    }
                    .sortedWith(languageOrder)

                installed to available
            }
                .collectLatest { (installed, available) ->
                    state.update {
                        it.copy(isLoading = false, installed = installed, available = available)
                    }
                }
        }

        // The lens is read separately from the list build so that flipping it is instant even while
        // a repository fetch is in flight.
        contentPreferences.contentFilter.changes()
            .onEach { lens -> state.update { it.copy(lens = lens) } }
            .launchIn(viewModelScope)

        combine(getStoreCount(), getAnimeStoreCount()) { manga, anime -> (manga + anime).toInt() }
            .onEach { count -> state.update { it.copy(storeCount = count) } }
            .launchIn(viewModelScope)

        viewModelScope.launchIO { refresh() }
    }

    fun search(query: String?) {
        state.update { it.copy(searchQuery = query) }
    }

    /** Ask both halves' repositories what they have. Either failing leaves the other alone. */
    suspend fun refresh() {
        runCatching { extensionManager.findAvailableExtensions() }
        runCatching { animeExtensionManager.findAvailableExtensions() }
    }

    fun install(row: ExtensionRow) {
        viewModelScope.launchIO {
            when (val handle = row.handle) {
                is ExtensionHandle.Manga -> {
                    val extension = handle.extension as? Extension.Available ?: return@launchIO
                    extensionManager.installExtension(extension).track(extension.pkgName)
                }
                is ExtensionHandle.Anime -> {
                    val extension = handle.extension as? AnimeExtension.Available ?: return@launchIO
                    animeExtensionManager.installExtension(extension).track(extension.pkgName)
                }
            }
        }
    }

    fun update(row: ExtensionRow) {
        viewModelScope.launchIO {
            when (val handle = row.handle) {
                is ExtensionHandle.Manga -> {
                    val extension = handle.extension as? Extension.Installed ?: return@launchIO
                    extensionManager.updateExtension(extension).track(extension.pkgName)
                }
                is ExtensionHandle.Anime -> {
                    val extension = handle.extension as? AnimeExtension.Installed ?: return@launchIO
                    animeExtensionManager.updateExtension(extension).track(extension.pkgName)
                }
            }
        }
    }

    fun updateAll() {
        state.value.installed.filter { it.hasUpdate }.forEach(::update)
    }

    fun uninstall(row: ExtensionRow) {
        when (val handle = row.handle) {
            is ExtensionHandle.Manga -> extensionManager.uninstallExtension(handle.extension)
            is ExtensionHandle.Anime -> animeExtensionManager.uninstallExtension(handle.extension)
        }
    }

    fun trust(row: ExtensionRow) {
        viewModelScope.launchIO {
            when (val handle = row.handle) {
                is ExtensionHandle.Manga ->
                    (handle.extension as? Extension.Untrusted)?.let { extensionManager.trust(it) }
                is ExtensionHandle.Anime ->
                    (handle.extension as? AnimeExtension.Untrusted)?.let { animeExtensionManager.trust(it) }
            }
        }
    }

    fun cancel(row: ExtensionRow) {
        when (val handle = row.handle) {
            is ExtensionHandle.Manga -> extensionManager.cancelInstallUpdateExtension(handle.extension)
            is ExtensionHandle.Anime -> animeExtensionManager.cancelInstallUpdateExtension(handle.extension)
        }
    }

    private suspend fun Flow<InstallStep>.track(pkgName: String) =
        onEach { step -> currentSteps.update { it + (pkgName to step) } }
            .onCompletion { currentSteps.update { it - pkgName } }
            .collect()

    private companion object {
        /**
         * Arabic first, then everything else by the platform's own order.
         *
         * The rest of the app treats Arabic as a first-class language rather than one more row in
         * an alphabetical list, and this is the screen where that matters most: an Arabic reader
         * whose language is buried under twenty others concludes there is nothing for them.
         */
        val languageOrder = compareBy<ExtensionLanguageGroup>(
            { if (it.languageCode == "ar") 0 else 1 },
            { it.languageName },
        )
    }
}

private fun Extension.toRow(steps: Map<String, InstallStep>) = ExtensionRow(
    key = "manga-$pkgName",
    name = name,
    lang = lang.orEmpty(),
    versionName = versionName,
    isNsfw = isNsfw,
    hasUpdate = (this as? Extension.Installed)?.hasUpdate == true,
    isObsolete = (this as? Extension.Installed)?.isObsolete == true,
    isUntrusted = this is Extension.Untrusted,
    isInstalled = this !is Extension.Available,
    installStep = steps[pkgName] ?: InstallStep.Idle,
    handle = ExtensionHandle.Manga(this),
)

private fun AnimeExtension.toRow(steps: Map<String, InstallStep>) = ExtensionRow(
    key = "anime-$pkgName",
    name = name,
    lang = lang.orEmpty(),
    versionName = versionName,
    isNsfw = isNsfw,
    hasUpdate = (this as? AnimeExtension.Installed)?.hasUpdate == true,
    isObsolete = (this as? AnimeExtension.Installed)?.isObsolete == true,
    isUntrusted = this is AnimeExtension.Untrusted,
    isInstalled = this !is AnimeExtension.Available,
    installStep = steps[pkgName] ?: InstallStep.Idle,
    handle = ExtensionHandle.Anime(this),
)

private fun ExtensionRow.matches(query: String?): Boolean {
    val trimmed = query?.trim().orEmpty()
    return trimmed.isEmpty() || name.contains(trimmed, ignoreCase = true)
}
