package animato.app.stremio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.stremio.DirectoryAddon
import animato.anime.stremio.StremioAddon
import animato.anime.stremio.StremioSource
import animato.anime.stremio.StremioUrls
import animato.app.source.SourceBrowseScreen
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus

/**
 * The addons, and the one field that adds one.
 *
 * This screen exists because adding a source here is nothing like installing an extension, and
 * pretending otherwise would be a lie the first error message would expose. There is no package to
 * trust, no signature to compare, no repository to browse — there is an address, and either it
 * answers like an addon or it does not. So the screen is a list and a text field, and every way it
 * can go wrong is said in a sentence under that field rather than as a toast that disappears
 * before it can be read.
 */
class StremioAddonsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = viewModel { StremioAddonsScreenModel() }
        val addons by screenModel.addons.collectAsStateWithLifecycle()
        val directory by screenModel.directoryAddons.collectAsStateWithLifecycle()
        val installState by screenModel.installState.collectAsStateWithLifecycle()
        var dialogOpen by remember { mutableStateOf(false) }
        // What the address field opens on. A suggestion fills it; the plus button leaves it blank.
        var draftUrl by remember { mutableStateOf("") }

        // Manifests go stale — an addon adds a catalog and this one would never notice. The
        // catch-up is silent and keeps whatever it already had wherever an addon does not answer.
        LaunchedEffect(Unit) { screenModel.refresh() }

        // Closing on success is the model's decision, not the dialog's: the dialog cannot know
        // whether the address it sent turned out to be an addon.
        LaunchedEffect(installState) {
            if (installState is AddonInstallState.Added) {
                dialogOpen = false
                screenModel.acknowledge()
            }
        }

        if (dialogOpen) {
            AddAddonDialog(
                initialUrl = draftUrl,
                state = installState,
                onAdd = screenModel::install,
                onDismiss = {
                    dialogOpen = false
                    screenModel.acknowledge()
                },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.stremio_addons),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                draftUrl = ""
                                dialogOpen = true
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = stringResource(AYMR.strings.stremio_add_addon),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
            ) {
                stremioAddons(
                    addons = addons,
                    onOpen = { addon ->
                        navigator.push(SourceBrowseScreen(StremioSource.idFor(addon.url), ContentType.ANIME))
                    },
                    onRemove = screenModel::remove,
                    onAdd = { url ->
                        draftUrl = url
                        dialogOpen = true
                    },
                    directory = directory,
                )
            }
        }
    }
}

/**
 * The addon list, wherever it is being drawn.
 *
 * It has two homes: its own screen, reached from Sources, and a segment beside Installed and
 * Available on that same screen — from a device, *"put Stremio next to installed and available"*.
 * Both are the same list, so it is one function rather than two that drift apart, and it is
 * [LazyListScope] rather than a composable because the segment it lives in is already a lazy list
 * and nesting a second scroller inside one is how a page ends up with two.
 */
internal fun LazyListScope.stremioAddons(
    addons: List<StremioAddon>,
    onOpen: (StremioAddon) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    /**
     * Stremio's own community list, when the caller has it.
     *
     * Empty from the segment beside Installed and Available, which is a summary of what is already
     * here and sends people to the full screen to add anything. Ninety rows do not belong under a
     * tab somebody swiped onto by accident.
     */
    directory: List<DirectoryAddon> = emptyList(),
) {
    // Suggestions the person already took are not suggestions any more.
    val installed = addons.map { StremioUrls.normalizeBase(it.url) }.toSet()
    val suggestions = SUGGESTED_ADDONS.filterNot { StremioUrls.normalizeBase(it.url) in installed }
    if (addons.isEmpty()) {
        item(key = "stremio-empty") {
            AnimatoEmptyState(
                message = stringResource(AYMR.strings.stremio_addons_empty),
                actionLabel = stringResource(AYMR.strings.stremio_add_addon),
                onAction = { onAdd("") },
            )
        }
    }

    if (suggestions.isNotEmpty()) {
        item(key = "stremio-suggested-header") {
            Text(
                text = stringResource(AYMR.strings.stremio_suggested),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.medium,
                    bottom = MaterialTheme.padding.small,
                ),
            )
        }
        items(items = suggestions, key = { "stremio-suggested-" + it.url }) { suggestion ->
            SuggestedAddonItem(suggestion = suggestion, onPick = { onAdd(suggestion.url) })
        }
    }

    items(items = addons, key = { "stremio-" + it.url }) { addon ->
        AddonListItem(
            addon = addon,
            // An addon with a catalogue is somewhere you can go, the same way an installed
            // extension is. A stream-only one is not — it has nothing to show, and opening an
            // empty grid would be a worse answer than not offering to open it.
            onOpen = if (addon.isBrowsable) ({ onOpen(addon) }) else null,
            onRemove = { onRemove(addon.url) },
        )
    }

    val community = directory.filterNot { StremioUrls.normalizeBase(it.url) in installed }
    if (community.isNotEmpty()) {
        item(key = "stremio-community-header") {
            SectionHeader(
                title = stringResource(AYMR.strings.stremio_community),
                subtitle = stringResource(AYMR.strings.stremio_community_summary),
            )
        }
        items(items = community, key = { "stremio-community-" + it.url }) { entry ->
            DirectoryAddonItem(entry = entry, onPick = { onAdd(entry.url) })
        }
    }
}

/**
 * A heading over a run of rows, with a line saying what they are.
 *
 * The suggestions had a bare title and did not need more — four entries we chose. The community
 * list does: it is long, it is somebody else's, and the second line is where that is said rather
 * than left for the person to infer from the length.
 */
@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.padding(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            top = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.small,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One row of the community list.
 *
 * The supporting line is the addon's own description where it wrote one, and the resources it
 * declares where it did not — which is less friendly and more useful than a blank, because
 * *catalog · meta* against a name nobody recognises is still the answer to what it would do here.
 *
 * Tapping fills the address field rather than installing, for the same reason the suggestions do:
 * several of these are configured on their own site first and carry the configuration in the path,
 * and an install button would hide the one thing that has to be edited.
 */
@Composable
private fun DirectoryAddonItem(entry: DirectoryAddon, onPick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPick),
        headlineContent = { Text(entry.name) },
        supportingContent = {
            Text(
                text = entry.description.takeIf { it.isNotBlank() }
                    ?: entry.resources.joinToString(" · "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(AYMR.strings.stremio_add_addon),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

@Composable
private fun AddonListItem(
    addon: StremioAddon,
    onOpen: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier,
        headlineContent = {
            Text(
                text = addon.manifest.name.ifBlank { addon.url },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                // What this addon is *for* comes before what it says about itself. A stream-only
                // addon never appears in the sources list, so without this line its absence there
                // looks like it failed to install.
                if (!addon.isBrowsable) {
                    Text(
                        text = stringResource(
                            when (addon.supplies) {
                                StremioAddon.Supplies.STREAMS -> AYMR.strings.stremio_supplies_streams
                                StremioAddon.Supplies.SUBTITLES -> AYMR.strings.stremio_supplies_subtitles
                                StremioAddon.Supplies.STREAMS_AND_SUBTITLES ->
                                    AYMR.strings.stremio_supplies_both
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val description = addon.manifest.description.takeIf { it.isNotBlank() }
                if (description != null) {
                    Text(text = description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = addon.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                )
            }
        },
    )
}

@Composable
private fun AddAddonDialog(
    initialUrl: String,
    state: AddonInstallState,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the address it was opened with, so picking a second suggestion without closing the
    // dialog replaces the field rather than leaving the first one in it.
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    val working = state is AddonInstallState.Working
    val submit = { if (url.isNotBlank() && !working) onAdd(url) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(AYMR.strings.stremio_add_addon)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(AYMR.strings.stremio_addon_url_hint)) },
                    supportingText = { Text(stringResource(AYMR.strings.stremio_addon_url_example)) },
                    singleLine = true,
                    enabled = !working,
                    isError = state is AddonInstallState.Failed,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )
                // The failure stays on screen next to the field that caused it, because every one
                // of these is something the person typing can fix on the spot.
                (state as? AddonInstallState.Failed)?.let {
                    Text(
                        text = it.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = url.isNotBlank() && !working) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(MR.strings.action_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

/**
 * One addon worth having, with what it does and nothing else.
 *
 * No logo and no install button. A logo would be a network fetch for a row that exists to be read
 * once, and a bare install button would hide the address — which is the one thing somebody needs to
 * understand here, and the thing Torrentio in particular needs them to edit.
 */
@Composable
private fun SuggestedAddonItem(
    suggestion: SuggestedAddon,
    onPick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPick),
        headlineContent = { Text(suggestion.name) },
        supportingContent = { Text(stringResource(suggestion.description)) },
        trailingContent = {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(AYMR.strings.stremio_add_addon),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    )
}
