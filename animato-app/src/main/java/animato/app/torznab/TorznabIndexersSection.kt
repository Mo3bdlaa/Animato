package animato.app.torznab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import animato.anime.torznab.TorznabIndexer
import animato.anime.torznab.TorznabIndexerStore
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The indexers added, and the way to add another.
 *
 * Beside the media servers rather than in a segment of its own, because they are the same kind of
 * thing from this screen's point of view: something the person runs themselves, added by typing an
 * address, with no directory to browse because the only one that exists is theirs.
 */
fun LazyListScope.torznabIndexers(
    indexers: List<TorznabIndexer>,
    onOpen: (TorznabIndexer) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    item(key = "torznab-add") {
        ListItem(
            modifier = Modifier.clickable(onClick = onAdd),
            headlineContent = { Text(stringResource(AYMR.strings.indexers_add)) },
            supportingContent = { Text(stringResource(AYMR.strings.indexers_summary)) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }

    items(items = indexers, key = { "torznab-" + it.url }) { indexer ->
        ListItem(
            modifier = Modifier.clickable { onOpen(indexer) },
            headlineContent = { Text(indexer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            // The address, without the key that is also in it. A settings list is the screen
            // people hand to somebody else to look at.
            supportingContent = {
                Text(indexer.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                IconButton(onClick = { onRemove(indexer.url) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(MR.strings.action_delete),
                    )
                }
            },
        )
    }
}

/**
 * Add an indexer.
 *
 * The address and the key, which is exactly the pair somebody already copied out of Jackett or
 * Prowlarr to configure Sonarr — so the fields are labelled the way those dashboards label them
 * rather than in this app's own words.
 *
 * The name is optional and falls back to the host. Somebody running one indexer never needs it;
 * somebody running six needs it badly, and asking everybody to invent one would be a required
 * field that is usually noise.
 */
@Composable
fun TorznabAddDialog(
    store: TorznabIndexerStore,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(AYMR.strings.indexers_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        failure = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(AYMR.strings.indexers_address)) },
                    placeholder = { Text(stringResource(AYMR.strings.indexers_address_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = failure != null,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        failure = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.small),
                    label = { Text(stringResource(AYMR.strings.indexers_key)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = failure != null,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.small),
                    label = { Text(stringResource(AYMR.strings.indexers_name)) },
                    singleLine = true,
                    enabled = !busy,
                )
                failure?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = MaterialTheme.padding.small),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && address.isNotBlank() && apiKey.isNotBlank(),
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        store.add(address, apiKey, label)
                            .onSuccess {
                                busy = false
                                onAdded()
                            }
                            .onFailure {
                                busy = false
                                failure = it.message
                            }
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (busy) AYMR.strings.indexers_checking else AYMR.strings.indexers_add_action,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
