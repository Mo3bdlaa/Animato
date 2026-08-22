package animato.app.nsfw

import animato.anime.stremio.StremioAddonDirectory
import animato.anime.stremio.StremioAddonStore
import animato.anime.stremio.StremioUrls
import animato.domain.content.ContentPreferences
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.flow.combine
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The two NSFW defaults this fork flips, both asked for from a device in the same breath:
 * *"disable showing nsfw by default"* and *"nsfw is incognito by default."*
 *
 * ## Hidden by default
 *
 * Mihon ships `showNsfwSource` defaulting to on, and the class that owns it is Mihon's, so the
 * default itself cannot be edited. What can be done is answering before the question is asked:
 * on a launch where nobody has ever touched the preference, it is set to off. `isSet` is the
 * difference between a default and a choice — someone who turned it on in settings has made a
 * choice, and this never runs again over it.
 *
 * ## Incognito by default
 *
 * "NSFW is incognito" cannot be a code change either, because the manga half's incognito resolver
 * is Mihon's and final. But the resolver reads a *set of packages*, and sets are data: every
 * installed extension flagged NSFW gets its package added to the matching incognito set, once.
 * Once, and remembered — [ContentPreferences.nsfwIncognitoSeeded] — so turning incognito off for
 * one of them afterwards is a decision the app keeps, not a fight it re-loses every launch.
 * History, progress and tracking all gate on those sets already; nothing else has to know.
 *
 * ## And the source kind that was slipping through both
 *
 * Neither of the above reaches a Stremio addon: the first hides sources the *extension* lists
 * declare NSFW, and the second files them under a package name an addon does not have. See
 * [seedIncognitoForAdultAddons].
 */
object NsfwDefaults {

    fun seedHiddenByDefault() {
        val showNsfw = Injekt.get<SourcePreferences>().showNsfwSource
        if (!showNsfw.isSet()) {
            showNsfw.set(false)
        }
    }

    /** Never completes; collect it from a scope that lives as long as the UI does. */
    suspend fun seedIncognitoForNsfw() {
        val sourcePreferences = Injekt.get<SourcePreferences>()
        val animeSourcePreferences = Injekt.get<AnimeSourcePreferences>()
        val contentPreferences = Injekt.get<ContentPreferences>()

        combine(
            Injekt.get<ExtensionManager>().installedExtensionsFlow,
            Injekt.get<AnimeExtensionManager>().installedExtensionsFlow,
        ) { manga, anime ->
            Pair(
                manga.filter { it.isNsfw }.map { it.pkgName },
                anime.filter { it.isNsfw }.map { it.pkgName },
            )
        }.collect { (mangaNsfw, animeNsfw) ->
            val seeded = contentPreferences.nsfwIncognitoSeeded.get()
            val newManga = mangaNsfw.filterNot { it in seeded }
            val newAnime = animeNsfw.filterNot { it in seeded }
            if (newManga.isEmpty() && newAnime.isEmpty()) return@collect

            if (newManga.isNotEmpty()) {
                sourcePreferences.incognitoExtensions.set(
                    sourcePreferences.incognitoExtensions.get() + newManga,
                )
            }
            if (newAnime.isNotEmpty()) {
                animeSourcePreferences.incognitoAnimeExtensions.set(
                    animeSourcePreferences.incognitoAnimeExtensions.get() + newAnime,
                )
            }
            contentPreferences.nsfwIncognitoSeeded.set(seeded + newManga + newAnime)
        }
    }

    /**
     * The same rule, for the kind of source the rule was quietly skipping.
     *
     * An installed extension carries an NSFW flag and a package name, and [seedIncognitoForNsfw]
     * uses both. A Stremio addon has neither: nothing in the manifest format says *adult*, and
     * there is no package. So an adult addon was the one source in the app that stayed on the
     * record by default — in the one place where adult content is most of what is on offer.
     *
     * Both halves come from somewhere else. Whether an addon is adult is the directory's answer,
     * decided offline from the addon's own words; the key it is filed under is its address, which
     * [eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState] falls back to for a source
     * with no package. Seeded once and remembered, like the other one, so that turning incognito
     * off for a particular addon afterwards is a decision the app keeps.
     *
     * Never completes; collect it from a scope that lives as long as the UI does.
     */
    suspend fun seedIncognitoForAdultAddons() {
        val animeSourcePreferences = Injekt.get<AnimeSourcePreferences>()
        val contentPreferences = Injekt.get<ContentPreferences>()
        val adult = Injekt.get<StremioAddonDirectory>().adultUrls()
        if (adult.isEmpty()) return

        Injekt.get<StremioAddonStore>().addons.collect { addons ->
            val seeded = contentPreferences.nsfwIncognitoSeeded.get()
            val fresh = addons
                .map { it.url }
                .filter { StremioUrls.normalizeBase(it) in adult }
                .filterNot { it in seeded }
            if (fresh.isEmpty()) return@collect

            animeSourcePreferences.incognitoAnimeExtensions.set(
                animeSourcePreferences.incognitoAnimeExtensions.get() + fresh,
            )
            contentPreferences.nsfwIncognitoSeeded.set(seeded + fresh)
        }
    }
}
