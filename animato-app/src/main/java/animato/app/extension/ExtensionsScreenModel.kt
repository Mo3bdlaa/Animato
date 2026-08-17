package animato.app.extension

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.service.SourcePreferences
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
import kotlinx.coroutines.flow.transformWhile
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

/**
 * Why an install failed, in the terms somebody can act on.
 *
 * The design sheet's rule is that a failure states its reason, and "Install failed" states none.
 * Android does not hand the reason back through anything reachable from here — the installer that
 * knows it is `internal` to Mihon — so the one cause that can be established without it is
 * established, and everything else says so honestly rather than inventing a diagnosis.
 */
@Immutable
enum class InstallFailure {
    /**
     * The update is signed by a different repository than the copy on the phone.
     *
     * Android refuses to replace a package with one signed by another key, and no amount of
     * retrying changes that — which is exactly what a button that keeps offering the same update
     * looks like from outside. The extension has to be uninstalled first.
     *
     * Established rather than guessed: both halves' stores carry a `signingKey`, and the installed
     * extension remembers which store it came from.
     */
    DIFFERENT_REPOSITORY,

    /** It failed and the reason is not ours to know. Said plainly instead of dressed up. */
    UNKNOWN,
}

/** What a package is doing, and — when it stopped badly — why. */
@Immutable
data class InstallActivity(
    val step: InstallStep,
    val failure: InstallFailure? = null,
)

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
    val failure: InstallFailure? = null,
    /**
     * A `Drawable` once installed, an icon URL before that, and null for an untrusted package.
     *
     * `Any?` because those are genuinely two things and the image loader takes either. An installed
     * extension's icon comes from the package manager and has no URL; an available one is a file on
     * a repository and is not on the device at all.
     */
    val icon: Any?,
    val handle: ExtensionHandle,
) {
    val contentType: ContentType get() = handle.contentType
}

/**
 * One language, and whether extensions written in it are being listed.
 *
 * Both halves read the same preference — hiding French hides it for anime and manga alike — which is
 * why this is one list rather than one per content type.
 */
@Immutable
data class ExtensionLanguage(
    val code: String,
    val name: String,
    val enabled: Boolean,
)

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
    val languages: List<ExtensionLanguage> = emptyList(),
) {
    val hasUpdates: Boolean get() = installed.any { it.hasUpdate }

    /**
     * Whether the language filter is hiding anything.
     *
     * Marked on the filter button, because a list shortened by a filter and a list that is short
     * look identical — and this screen defaults to hiding every language but the device's.
     */
    val isLanguageFiltered: Boolean get() = languages.any { !it.enabled }
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
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<ExtensionsState>
        field = MutableStateFlow(ExtensionsState(lens = contentPreferences.contentFilter.get()))

    /**
     * What each package is doing right now, keyed by package name.
     *
     * Held outside the state so that a download step ticking over does not re-run the grouping and
     * sorting of every list on the screen.
     */
    private val currentSteps = MutableStateFlow<Map<String, InstallActivity>>(emptyMap())

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
                val onDevice = InstalledVersions(context)
                val installed = buildList {
                    if (lens.includesManga) {
                        // Updates are listed among the rest rather than in a pending section of
                        // their own. The Update pill on the row already says which ones, and a
                        // section that empties itself moves every row below it on each install.
                        addAll((manga.updates + manga.installed).map { it.toRow(steps, onDevice) })
                        addAll(manga.untrusted.map { it.toRow(steps, onDevice) })
                    }
                    if (lens.includesAnime) {
                        addAll((anime.updates + anime.installed).map { it.toRow(steps, onDevice) })
                        addAll(anime.untrusted.map { it.toRow(steps, onDevice) })
                    }
                }
                    .filter { it.matches(query) }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val available = buildList {
                    if (lens.includesManga) addAll(manga.available.map { it.toRow(steps, onDevice) })
                    if (lens.includesAnime) addAll(anime.available.map { it.toRow(steps, onDevice) })
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

        /*
         * The languages, read from the *unfiltered* lists.
         *
         * `GetExtensionsByType` has already applied the filter by the time the rows arrive, so
         * asking it which languages exist would only ever return the ones already enabled — a
         * filter that can be turned off and never on again. The managers' own flows are what the
         * interactors filter, so they are what the control has to be built from.
         */
        combine(
            extensionManager.availableExtensionsFlow,
            animeExtensionManager.availableExtensionsFlow,
            sourcePreferences.enabledLanguages.changes(),
        ) { manga, anime, enabled ->
            val offered = manga.flatMap { ext -> ext.sources.map { it.lang } } +
                anime.flatMap { ext -> ext.sources.map { it.lang } }
            // Union with the enabled set, not just what is on offer: a language somebody turned on
            // before their repository went down would otherwise vanish from the list while still
            // filtering, which is a control that cannot be reached to undo.
            (offered + enabled)
                .distinct()
                .map {
                    ExtensionLanguage(
                        code = it,
                        name = LocaleHelper.getSourceDisplayName(it, context),
                        enabled = it in enabled,
                    )
                }
                .sortedWith(languageChoiceOrder)
        }
            .onEach { languages -> state.update { it.copy(languages = languages) } }
            .launchIn(viewModelScope)

        viewModelScope.launchIO { refresh() }
    }

    /**
     * Turns one language on or off.
     *
     * Writes the shared preference rather than a screen-local set, so it is the same filter Mihon's
     * own browse screens read and it survives leaving the screen. That is also why it is not called
     * a "filter" in the interface: it is a standing choice about which languages this install is
     * about, not a temporary narrowing of a list.
     */
    fun toggleLanguage(code: String) {
        val enabled = sourcePreferences.enabledLanguages.get()
        sourcePreferences.enabledLanguages.set(
            if (code in enabled) enabled - code else enabled + code,
        )
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
                    extensionManager.installExtension(extension).track(extension.pkgName, ContentType.MANGA)
                }
                is ExtensionHandle.Anime -> {
                    val extension = handle.extension as? AnimeExtension.Available ?: return@launchIO
                    animeExtensionManager.installExtension(extension).track(extension.pkgName, ContentType.ANIME)
                }
            }
        }
    }

    fun update(row: ExtensionRow) {
        viewModelScope.launchIO {
            when (val handle = row.handle) {
                is ExtensionHandle.Manga -> {
                    val extension = handle.extension as? Extension.Installed ?: return@launchIO
                    extensionManager.updateExtension(extension).track(extension.pkgName, ContentType.MANGA)
                }
                is ExtensionHandle.Anime -> {
                    val extension = handle.extension as? AnimeExtension.Installed ?: return@launchIO
                    animeExtensionManager.updateExtension(extension).track(extension.pkgName, ContentType.ANIME)
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

    /**
     * Follows one install to its end, and stops there.
     *
     * ## Two bugs in four lines
     *
     * The installer hands back `MutableStateFlow.asStateFlow()`, and **a state flow never
     * completes**. So `collect()` never returned: every install left a coroutine collecting for the
     * life of the screen, and `onCompletion` — the line that was supposed to clear the row's
     * progress — could not run at all. `transformWhile` ends the collection at the first terminal
     * step, which is what makes both work.
     *
     * The second is that `Error` is terminal and was being dropped on the floor with the rest.
     * Nothing on the row said an install had failed, so a failed install and a button that does
     * nothing looked exactly alike — which is how *"I update it and it still says update, no matter
     * how many times"* gets reported. A failure is kept on the row now until the next attempt.
     */
    private suspend fun Flow<InstallStep>.track(pkgName: String, contentType: ContentType) =
        transformWhile { step ->
            emit(step)
            !step.isCompleted()
        }
            .onEach { step ->
                val failure = if (step == InstallStep.Error) diagnose(pkgName, contentType) else null
                currentSteps.update { it + (pkgName to InstallActivity(step, failure)) }
            }
            .onCompletion {
                currentSteps.update { steps ->
                    // A failure stays. Anything else has nothing left to say, and the row goes back
                    // to being described by the extension itself.
                    if (steps[pkgName]?.step == InstallStep.Error) steps else steps - pkgName
                }
            }
            .collect()

    /**
     * The one cause of a failed install that can be established from here.
     *
     * Android will not replace a package with one signed by a different key. Both halves' stores
     * carry the key they sign with, and an installed extension remembers the store it came from —
     * so when the store now offering the update signs with a different key, the refusal is certain
     * and no amount of retrying will change it. With five repositories configured, installing from
     * one and being offered the update by another is not an unusual thing to have happen.
     *
     * Everything else is [InstallFailure.UNKNOWN]. The installer that knows the real reason is
     * `internal` to Mihon and does not pass it out, and a guess dressed as a diagnosis is worse
     * than saying so.
     */
    private fun diagnose(pkgName: String, contentType: ContentType): InstallFailure {
        val differentKey = when (contentType) {
            ContentType.MANGA -> {
                val installed = extensionManager.installedExtensionsFlow.value
                    .firstOrNull { it.pkgName == pkgName }?.store?.signingKey
                val available = extensionManager.availableExtensionsFlow.value
                    .firstOrNull { it.pkgName == pkgName }?.store?.signingKey
                installed != null && available != null && installed != available
            }
            ContentType.ANIME -> {
                val installed = animeExtensionManager.installedExtensionsFlow.value
                    .firstOrNull { it.pkgName == pkgName }?.store?.signingKey
                val available = animeExtensionManager.availableExtensionsFlow.value
                    .firstOrNull { it.pkgName == pkgName }?.store?.signingKey
                installed != null && available != null && installed != available
            }
        }
        return if (differentKey) InstallFailure.DIFFERENT_REPOSITORY else InstallFailure.UNKNOWN
    }

    private companion object {
        /**
         * Arabic first, then everything else by the platform's own order.
         *
         * The rest of the app treats Arabic as a first-class language rather than one more row in
         * an alphabetical list, and this is the screen where that matters most: an Arabic reader
         * whose language is buried under twenty others concludes there is nothing for them.
         */
        val languageOrder = compareBy<ExtensionLanguageGroup>(
            { if (it.languageCode == ARABIC) 0 else 1 },
            { it.languageName },
        )

        /**
         * The same rule for the filter itself, and deliberately blind to whether one is enabled.
         *
         * Putting the enabled ones first would be useful in a list of forty — and would move the
         * row out from under the finger that just ticked it, every single time. A control that
         * rearranges itself as you use it is worse than one that makes you scroll.
         */
        val languageChoiceOrder = compareBy<ExtensionLanguage>(
            { if (it.code == ARABIC) 0 else 1 },
            { it.name },
        )

        const val ARABIC = "ar"
    }
}

/**
 * What is actually installed, asked of the system rather than of a cache.
 *
 * ## The bug
 *
 * From a device: *"every time I update the extensions they keep telling me they need an update, as
 * if I never did."*
 *
 * `hasUpdate` is a field on the manager's in-memory record of an installed extension, and that
 * record is only replaced when a broadcast about the package arrives. Between the update landing
 * and that broadcast being received — or if it is never received, which is what a phone that keeps
 * offering the same update is telling you — the record still holds the *old* version code, that
 * version is still behind the repository's, and the row still says Update. Pressing it installs the
 * same version again and changes nothing, forever.
 *
 * The manager exposes no way to reload that record, and it is Mihon's file. So this asks the package
 * manager what is on the phone right now, and only believes `hasUpdate` when the installed package
 * really is no newer than the record claims.
 *
 * It can only ever *withdraw* an update, never invent one. Being told about an update that does not
 * exist is the fault being fixed; inventing one would be the same fault with a new cause.
 *
 * A package that is not found is a private extension — those live in the app's own data directory
 * and are not packages at all — and there the record is the only answer there is, so it stands.
 *
 * One instance per rebuild of the list, so a package is asked about once however many rows name it.
 */
private class InstalledVersions(context: Context) {

    private val packageManager = context.packageManager
    private val cache = mutableMapOf<String, Long?>()

    private fun versionOf(pkgName: String): Long? = cache.getOrPut(pkgName) {
        runCatching {
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(pkgName, 0))
        }.getOrNull()
    }

    fun stillOlderThan(extension: Extension): Boolean =
        (versionOf(extension.pkgName) ?: return true) <= extension.versionCode

    fun stillOlderThan(extension: AnimeExtension): Boolean =
        (versionOf(extension.pkgName) ?: return true) <= extension.versionCode
}

private fun Extension.toRow(steps: Map<String, InstallActivity>, onDevice: InstalledVersions) = ExtensionRow(
    key = "manga-$pkgName",
    name = name,
    lang = lang.orEmpty(),
    versionName = versionName,
    isNsfw = isNsfw,
    hasUpdate = (this as? Extension.Installed)?.hasUpdate == true && onDevice.stillOlderThan(this),
    isObsolete = (this as? Extension.Installed)?.isObsolete == true,
    isUntrusted = this is Extension.Untrusted,
    isInstalled = this !is Extension.Available,
    installStep = steps[pkgName]?.step ?: InstallStep.Idle,
    failure = steps[pkgName]?.failure,
    icon = extensionIcon(),
    handle = ExtensionHandle.Manga(this),
)

/**
 * Where an extension's logo comes from, which is a different place before and after installing.
 *
 * Once installed it is a `Drawable` the package manager already holds; before that it is a file on
 * the repository and the only thing on the device is its URL. An untrusted package has neither: it
 * is on disk but deliberately not loaded, and reading an icon out of it would be the one thing
 * being untrusted is supposed to prevent.
 */
private fun Extension.extensionIcon(): Any? = when (this) {
    is Extension.Installed -> icon
    is Extension.Available -> iconUrl
    is Extension.Untrusted -> null
}

private fun AnimeExtension.extensionIcon(): Any? = when (this) {
    is AnimeExtension.Installed -> icon
    is AnimeExtension.Available -> iconUrl
    is AnimeExtension.Untrusted -> null
}

private fun AnimeExtension.toRow(steps: Map<String, InstallActivity>, onDevice: InstalledVersions) = ExtensionRow(
    key = "anime-$pkgName",
    name = name,
    lang = lang.orEmpty(),
    versionName = versionName,
    isNsfw = isNsfw,
    hasUpdate = (this as? AnimeExtension.Installed)?.hasUpdate == true && onDevice.stillOlderThan(this),
    isObsolete = (this as? AnimeExtension.Installed)?.isObsolete == true,
    isUntrusted = this is AnimeExtension.Untrusted,
    isInstalled = this !is AnimeExtension.Available,
    installStep = steps[pkgName]?.step ?: InstallStep.Idle,
    failure = steps[pkgName]?.failure,
    icon = extensionIcon(),
    handle = ExtensionHandle.Anime(this),
)

private fun ExtensionRow.matches(query: String?): Boolean {
    val trimmed = query?.trim().orEmpty()
    return trimmed.isEmpty() || name.contains(trimmed, ignoreCase = true)
}
