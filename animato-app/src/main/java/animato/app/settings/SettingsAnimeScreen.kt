package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import animato.anime.player.settings.PlayerSettingsMainScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Everything about anime that is not also about manga.
 *
 * Aniyomi answered the same question by doubling the length of five of Mihon's settings screens.
 * One section instead: someone who never opens an anime never sees any of it, and Mihon's screens
 * stay exactly as Mihon wrote them, which is what makes an upstream update a compile error at worst
 * rather than a merge conflict.
 *
 * A setting appears here only when it means something different for anime. Where one value governs
 * the whole app — where downloads are stored, whether to show NSFW sources, which trackers you are
 * signed in to — it stays in Mihon's screen, because two controls over one value is worse than
 * either place owning it.
 */
object SettingsAnimeScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.label_anime

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.label_anime),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_anime_library),
                        onClick = { navigator.push(SettingsAnimeLibraryScreen) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_player),
                        onClick = { navigator.push(PlayerSettingsMainScreen(mainSettings = false)) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.label_downloaded),
                        onClick = { navigator.push(SettingsAnimeDownloadScreen) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.label_anime_sources),
                        onClick = { navigator.push(SettingsAnimeBrowseScreen) },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_category_tracking),
                        onClick = { navigator.push(SettingsAnimeTrackingScreen) },
                    ),
                ),
            ),
        )
    }
}
