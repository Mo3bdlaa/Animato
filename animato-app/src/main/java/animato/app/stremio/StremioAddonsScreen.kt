package animato.app.stremio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.stremio.AddonKind
import animato.anime.stremio.DirectoryAddon
import animato.anime.stremio.StremioAddon
import animato.anime.stremio.StremioSource
import animato.anime.stremio.StremioUrls
import animato.app.source.SourceBrowseScreen
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
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
        // Null is "the search box is closed", which is not the same as an empty query — one hides
        // the field and the other shows an empty one. SearchToolbar draws the difference.
        var query by remember { mutableStateOf<String?>(null) }
        var kind by remember { mutableStateOf<AddonKind?>(null) }

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
                // Searchable, because the community list is hundreds of rows long and scrolling
                // it to find one name is not browsing, it is looking for something.
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(AYMR.strings.stremio_addons)) },
                    searchQuery = query,
                    onChangeSearchQuery = { query = it },
                    placeholderText = stringResource(AYMR.strings.stremio_search_hint),
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
                addonStore(
                    addons = addons,
                    directory = directory,
                    query = query.orEmpty(),
                    kind = kind,
                    onKindChange = { kind = it },
                    showAdult = screenModel.showAdult,
                    onOpen = { addon ->
                        navigator.push(SourceBrowseScreen(StremioSource.idFor(addon.url), ContentType.ANIME))
                    },
                    onRemove = screenModel::remove,
                    onAdd = { url ->
                        draftUrl = url
                        dialogOpen = true
                    },
                )
            }
        }
    }
}

/**
 * The addons already here, as the Sources tab shows them.
 *
 * Browse and remove, and nothing else. It used to be the same list as the store — suggestions,
 * community rows, and an *add* action that lived **only in the empty state**. So the way to add a
 * second addon was to delete the first one: with anything installed, the empty state was gone and
 * with it the only entrance. Adding now lives under *Extension stores*, beside the two extension
 * repositories, where a person looking for somewhere to get sources from would go anyway.
 */
internal fun LazyListScope.installedAddons(
    addons: List<StremioAddon>,
    onOpen: (StremioAddon) -> Unit,
    onRemove: (String) -> Unit,
    onOpenStore: () -> Unit,
) {
    if (addons.isEmpty()) {
        item(key = "stremio-empty") {
            AnimatoEmptyState(
                message = stringResource(AYMR.strings.stremio_addons_empty),
                actionLabel = stringResource(AYMR.strings.stremio_addons_empty_action),
                onAction = onOpenStore,
            )
        }
        return
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
}

/**
 * The store: everything on offer, and what is already taken.
 *
 * Three runs of rows in the order somebody works through them — what you have, four we suggest,
 * and then everybody else's. All three answer the same search box, because "does this list have
 * Comet in it" is one question and it should not matter which section Comet turned out to be in.
 */
internal fun LazyListScope.addonStore(
    addons: List<StremioAddon>,
    directory: List<DirectoryAddon>,
    query: String,
    /**
     * Which kind of addon the community list is narrowed to, or null for all of them.
     *
     * Applied to that list only. The four suggestions are one of each on purpose and hiding three
     * of them would defeat what they are for, and what is already installed is not a shelf to
     * browse — a filter that emptied it would be answering a question nobody asked.
     */
    kind: AddonKind?,
    onKindChange: (AddonKind?) -> Unit,
    /** Whether adult addons are offered at all — see [StremioAddonsScreenModel.showAdult]. */
    showAdult: Boolean,
    onOpen: (StremioAddon) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    val installed = addons.map { StremioUrls.normalizeBase(it.url) }.toSet()
    // Every address the directory calls adult, including the ones already installed — which is
    // the only way an installed addon can be marked at all, since a manifest never says.
    val adultUrls = directory.filter { it.isAdult }.map { StremioUrls.normalizeBase(it.url) }.toSet()
    val mine = addons.filter { matches(query, it.manifest.name, it.manifest.description, it.url) }
    // Suggestions the person already took are not suggestions any more.
    val suggestions = SUGGESTED_ADDONS
        .filterNot { StremioUrls.normalizeBase(it.url) in installed }
        .filter { matches(query, it.name, null, it.url) }
    val searched = directory
        .filterNot { StremioUrls.normalizeBase(it.url) in installed }
        .filter { showAdult || !it.isAdult }
        .filter { matches(query, it.name, it.description, it.url) }
    // Counted before the kind filter, so a chip can say how many it would leave and an empty one
    // is visibly empty rather than missing.
    val counts = searched.groupingBy { it.kind }.eachCount()
    val community = searched.filter { kind == null || it.kind == kind }

    if (mine.isEmpty() && suggestions.isEmpty() && searched.isEmpty()) {
        item(key = "stremio-no-match") {
            AnimatoEmptyState(
                message = stringResource(
                    if (query.isBlank()) AYMR.strings.stremio_addons_empty else AYMR.strings.stremio_search_empty,
                ),
            )
        }
        return
    }

    if (mine.isNotEmpty()) {
        item(key = "stremio-installed-header") {
            SectionHeader(title = stringResource(AYMR.strings.stremio_installed))
        }
        items(items = mine, key = { "stremio-" + it.url }) { addon ->
            AddonListItem(
                addon = addon,
                onOpen = if (addon.isBrowsable) ({ onOpen(addon) }) else null,
                onRemove = { onRemove(addon.url) },
                isAdult = StremioUrls.normalizeBase(addon.url) in adultUrls,
            )
        }
    }

    if (suggestions.isNotEmpty()) {
        item(key = "stremio-suggested-header") {
            SectionHeader(title = stringResource(AYMR.strings.stremio_suggested))
        }
        items(items = suggestions, key = { "stremio-suggested-" + it.url }) { suggestion ->
            SuggestedAddonItem(suggestion = suggestion, onPick = { onAdd(suggestion.url) })
        }
    }

    if (searched.isNotEmpty()) {
        item(key = "stremio-community-header") {
            SectionHeader(
                title = stringResource(AYMR.strings.stremio_community),
                subtitle = stringResource(AYMR.strings.stremio_community_summary),
            )
        }
        item(key = "stremio-kind-chips") {
            KindChips(selected = kind, counts = counts, onSelect = onKindChange)
        }
        // The chosen kind, said once above the rows it explains. A chip has room for a name and
        // not for the consequence, and the consequence — *nothing plays until you also install a
        // video addon* — is the entire reason these are separated.
        if (kind != null) {
            item(key = "stremio-kind-summary") {
                Text(
                    text = stringResource(kind.summaryRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                )
            }
        }
        items(items = community, key = { "stremio-community-" + it.url }) { entry ->
            DirectoryAddonItem(entry = entry, onPick = { onAdd(entry.url) })
        }
    }
}

private val AddonKind.labelRes: StringResource
    get() = when (this) {
        AddonKind.Complete -> AYMR.strings.addon_kind_complete
        AddonKind.Video -> AYMR.strings.addon_kind_video
        AddonKind.Catalogue -> AYMR.strings.addon_kind_catalogue
        AddonKind.Subtitles -> AYMR.strings.addon_kind_subtitles
    }

private val AddonKind.summaryRes: StringResource
    get() = when (this) {
        AddonKind.Complete -> AYMR.strings.addon_kind_complete_summary
        AddonKind.Video -> AYMR.strings.addon_kind_video_summary
        AddonKind.Catalogue -> AYMR.strings.addon_kind_catalogue_summary
        AddonKind.Subtitles -> AYMR.strings.addon_kind_subtitles_summary
    }

/**
 * The four kinds, as chips, with how many of each the search left.
 *
 * Counts rather than bare names because the point of the row is to answer *what is in here* before
 * anything is tapped — "Catalogue only 87" is a fact about the list, and a chip that turns out to
 * be empty is worse than one that said so.
 *
 * Tapping the chosen chip clears it, so *All* and the chip itself are the same control seen twice
 * and nobody has to scroll back to the start to undo a filter.
 */
@Composable
private fun KindChips(
    selected: AddonKind?,
    counts: Map<AddonKind, Int>,
    onSelect: (AddonKind?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(AYMR.strings.addon_kind_all)) },
        )
        AddonKind.entries.forEach { kind ->
            val count = counts[kind] ?: 0
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(if (selected == kind) null else kind) },
                label = { Text("${stringResource(kind.labelRes)}  $count") },
            )
        }
    }
}

/**
 * Whether a row survives the search box.
 *
 * Matched against the address as well as the name and the description, because half of these are
 * known by their host — somebody looking for *torrentio* and somebody looking for *strem.fun* are
 * both looking for the same row, and only one of those is in the name.
 */
private fun matches(query: String, name: String, description: String?, url: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return name.contains(needle, ignoreCase = true) ||
        description?.contains(needle, ignoreCase = true) == true ||
        url.contains(needle, ignoreCase = true)
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
        // The kind first, then whatever the author wrote. Ahead of the description on purpose:
        // the descriptions are marketing and every one of them sounds like it plays films, while
        // this is read off the manifest and is the one line that says whether it will.
        overlineContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                Text(
                    text = stringResource(entry.kind.labelRes),
                    color = MaterialTheme.colorScheme.primary,
                )
                // The same 18+ in the same error colour Mihon puts on an NSFW extension. These
                // rows are only reachable with *Show NSFW sources* on, so it is not a warning
                // about something unexpected — it is the label that lets somebody scanning a
                // filtered list still tell which rows are which.
                if (entry.isAdult) {
                    Text(
                        text = "·",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(MR.strings.ext_nsfw_short),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
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
    /**
     * Whether the directory says this one serves adult content.
     *
     * Answered from the directory rather than from the addon, because the manifest has no field
     * for it — so an installed addon that is not in either published list simply is not marked,
     * and that is the honest answer rather than a guess dressed as a fact.
     */
    isAdult: Boolean = false,
) {
    ListItem(
        modifier = if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier,
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = addon.manifest.name.ifBlank { addon.url },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isAdult) {
                    Text(
                        text = stringResource(MR.strings.ext_nsfw_short),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
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
