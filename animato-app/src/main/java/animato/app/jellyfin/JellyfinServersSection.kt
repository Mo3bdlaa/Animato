package animato.app.jellyfin

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import animato.anime.jellyfin.JellyfinServer
import animato.anime.jellyfin.JellyfinServerStore
import animato.ui.components.AnimatoEmptyState
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The servers signed in to, and the way to sign in to another.
 *
 * ## Why adding lives here and not in the store
 *
 * The Stremio and IPTV segments deliberately have no add button — adding an addon or a playlist is
 * browsing a directory, which is a screen of its own. A server is the opposite: there is no
 * directory to browse, because the only server anybody can add is one they already run. So the add
 * is a dialog on this segment, and there is nothing else it could sensibly be.
 */
fun LazyListScope.jellyfinServers(
    servers: List<JellyfinServer>,
    onOpen: (JellyfinServer) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    item(key = "jellyfin-add") {
        ListItem(
            modifier = Modifier.clickable(onClick = onAdd),
            headlineContent = { Text(stringResource(AYMR.strings.servers_add)) },
            supportingContent = { Text(stringResource(AYMR.strings.servers_summary)) },
            leadingContent = {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }

    if (servers.isEmpty()) {
        item(key = "jellyfin-empty") {
            AnimatoEmptyState(message = stringResource(AYMR.strings.servers_empty))
        }
        return
    }

    items(items = servers, key = { "jellyfin-" + it.url }) { server ->
        ListItem(
            modifier = Modifier.clickable { onOpen(server) },
            headlineContent = {
                Text(hostOf(server.url), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            // The account, because two sign-ins to one machine are two libraries and the address
            // is the same word twice for them.
            supportingContent = {
                Text(stringResource(AYMR.strings.servers_signed_in_as, server.name), maxLines = 1)
            },
            trailingContent = {
                IconButton(onClick = { onRemove(server.url) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(AYMR.strings.servers_sign_out),
                    )
                }
            },
        )
    }
}

/**
 * Sign in to a server.
 *
 * Three fields and one of them may be empty: a Jellyfin account genuinely can have no password,
 * which is the default for the first account on a fresh install and is a setup people keep on a
 * home network. So the password field says so rather than the form refusing an empty one.
 *
 * The failure comes back into the dialog rather than as a snackbar behind it. Every failure here is
 * about something in one of these three fields, and a message that appears somewhere the fields are
 * not is a message that cannot be acted on without dismissing it first.
 */
@Composable
fun JellyfinSignInDialog(
    store: JellyfinServerStore,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var failure by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(AYMR.strings.servers_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        failure = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(AYMR.strings.servers_address)) },
                    placeholder = { Text(stringResource(AYMR.strings.servers_address_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = failure != null,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        failure = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.small),
                    label = { Text(stringResource(AYMR.strings.servers_username)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = failure != null,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        failure = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.padding.small),
                    label = { Text(stringResource(AYMR.strings.servers_password)) },
                    placeholder = { Text(stringResource(AYMR.strings.servers_password_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = failure != null,
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
                enabled = !busy && address.isNotBlank() && username.isNotBlank(),
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        store.signIn(address, username, password)
                            .onSuccess {
                                busy = false
                                onSignedIn()
                            }
                            .onFailure {
                                busy = false
                                failure = it.message
                            }
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = MaterialTheme.padding.small))
                    Text(stringResource(AYMR.strings.servers_signing_in))
                } else {
                    Text(stringResource(AYMR.strings.servers_sign_in))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
