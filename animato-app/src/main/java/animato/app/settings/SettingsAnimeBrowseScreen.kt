package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import aniyomi.domain.source.service.AnimeSourcePreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStoreCountAsFlow
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The anime half of the browse settings.
 *
 * Extension stores are per content type — an anime store serves anime extensions — so the count and
 * the editor are separate from Mihon's. Whether NSFW sources are shown is not: that is one decision
 * about the whole app and stays in Mihon's screen.
 */
object SettingsAnimeBrowseScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.label_anime_sources

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        val sourcePreferences = remember { Injekt.get<AnimeSourcePreferences>() }
        val getStoreCount = remember { Injekt.get<GetAnimeExtensionStoreCountAsFlow>() }
        val storeCount by getStoreCount().collectAsStateWithLifecycle(0)

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.label_sources),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = sourcePreferences.hideInAnimeLibraryItems,
                        title = stringResource(AYMR.strings.pref_hide_in_anime_library_items),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.extensionStores),
                        subtitle = pluralStringResource(MR.plurals.num_repos, storeCount.toInt(), storeCount),
                        onClick = { navigator.push(AnimeExtensionStoresScreen()) },
                    ),
                ),
            ),
        )
    }
}
