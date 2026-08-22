package animato.app.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import cafe.adriel.voyager.navigator.Navigator
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The two addresses that actually work, offered rather than typed.
 *
 * ## Why this exists
 *
 * The app ships with no stores, deliberately, and the screen for adding one is a text field
 * expecting a URL ending in `index.min.json`. That is honest about the mechanism and useless as a
 * starting point: somebody who has just installed the app cannot know what to put in it, and the
 * addresses are not the kind of thing anybody types from memory. Reported plainly from a device —
 * *I feel a lack of sources* — by somebody who had already found the screen.
 *
 * ## Why only two, and why one of them comes with a warning
 *
 * These are the two that answered when this was written, checked rather than remembered. The manga
 * one carries around thirteen hundred extensions and is the store the community actually uses.
 *
 * The anime one carries three, and all three are utilities — Google Drive, a Drive index, and
 * Jellyfin. That is not a bad snapshot, it is the state of the world: the public store of anime
 * streaming extensions is gone, and no replacement is published at a stable address. Offering it
 * without saying so would leave somebody tapping it, seeing three unfamiliar rows, and concluding
 * the app is broken. So the note under it says what is true, and points at the ways in that do
 * work — which is the whole reason this fork grew addons, playlists and servers.
 *
 * ## Why tapping does not add anything
 *
 * Each row opens the store screen for that half with the address filled in, which is the same path
 * a `tachiyomi://add-repo` link takes and ends at the same confirmation. A store decides what code
 * gets installed on the device; it is not something to hand over on one tap from a list the app
 * wrote.
 */
private data class KnownStore(
    val name: String,
    val indexUrl: String,
    val note: StringResource,
    val isAnime: Boolean,
)

private val KNOWN_STORES = listOf(
    KnownStore(
        name = "Keiyoushi",
        indexUrl = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
        note = AYMR.strings.known_store_keiyoushi,
        isAnime = false,
    ),
    KnownStore(
        name = "Official Aniyomi extensions",
        indexUrl = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json",
        note = AYMR.strings.known_store_aniyomi,
        isAnime = true,
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
                KNOWN_STORES.forEach { store ->
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
