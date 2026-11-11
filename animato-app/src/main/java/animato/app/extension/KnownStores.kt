package animato.app.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import animato.domain.content.ContentPreferences
import cafe.adriel.voyager.navigator.Navigator
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Store addresses that work, offered rather than typed.
 *
 * The app ships with no stores, and the screen for adding one expects a URL ending in
 * `index.min.json` — honest about the mechanism, useless to somebody who has just installed the app.
 *
 * Each one was checked when this list was written: its index and its `repo.json`, which carries the
 * signing key extensions are trusted by, both answered. A store without that file cannot be added,
 * so none is listed. They are run by their own authors; one can move or stop at any time.
 *
 * Tapping a row opens the store screen for that half with the address filled in, the same path a
 * `tachiyomi://add-repo` link takes. A store decides what code gets installed on the device, so it
 * is never added on one tap from a list the app wrote.
 */
private data class KnownStore(
    val name: String,
    val indexUrl: String,
    val note: StringResource,
    val isAnime: Boolean,
)

private val KNOWN_STORES = listOf(
    KnownStore(
        name = "Yūzōnō",
        indexUrl = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
        note = AYMR.strings.known_store_yuzono,
        isAnime = true,
    ),
    KnownStore(
        name = "SalmanBappi",
        indexUrl = "https://raw.githubusercontent.com/salmanbappi/extensions-repo/main/index.min.json",
        note = AYMR.strings.known_store_salmanbappi,
        isAnime = true,
    ),
    KnownStore(
        name = "Secozzi",
        indexUrl = "https://raw.githubusercontent.com/Secozzi/aniyomi-extensions/refs/heads/repo/index.min.json",
        note = AYMR.strings.known_store_secozzi,
        isAnime = true,
    ),
    KnownStore(
        name = "Official Aniyomi extensions",
        indexUrl = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json",
        note = AYMR.strings.known_store_aniyomi,
        isAnime = true,
    ),
    KnownStore(
        name = "Keiyoushi",
        indexUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
        note = AYMR.strings.known_store_keiyoushi,
        isAnime = false,
    ),
    KnownStore(
        name = "Kavita",
        indexUrl = "https://raw.githubusercontent.com/Kareadita/tach-extension/repo/index.min.json",
        note = AYMR.strings.known_store_kavita,
        isAnime = false,
    ),
    KnownStore(
        name = "Suwayomi",
        indexUrl = "https://raw.githubusercontent.com/suwayomi/tachiyomi-extension/repo/index.min.json",
        note = AYMR.strings.known_store_suwayomi,
        isAnime = false,
    ),
)

@Composable
internal fun KnownStoresDialog(
    navigator: Navigator,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(AYMR.strings.known_stores_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(AYMR.strings.known_stores_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Only the anime stores on a build that has no manga to read. See RepositoriesRow.
                val mangaUnavailable = remember { Injekt.get<ContentPreferences>().lensIsFixed }
                KNOWN_STORES.filter { it.isAnime || !mangaUnavailable }.forEach { store ->
                    ListItem(
                        modifier = Modifier.clickable {
                            onDismissRequest()
                            navigator.push(
                                if (store.isAnime) {
                                    AnimeExtensionStoresScreen(store.indexUrl)
                                } else {
                                    ExtensionStoresScreen(store.indexUrl)
                                },
                            )
                        },
                        headlineContent = { Text(store.name) },
                        supportingContent = { Text(stringResource(store.note)) },
                    )
                }
                Text(
                    text = stringResource(AYMR.strings.known_stores_anime_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
