package animato.app.extension

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.iptv.M3uPlaylistStore
import animato.anime.iptv.M3uSource
import animato.anime.jellyfin.JellyfinServerStore
import animato.anime.jellyfin.JellyfinSource
import animato.anime.stremio.StremioAddonStore
import animato.anime.stremio.StremioSource
import animato.anime.torznab.TorznabIndexerStore
import animato.anime.torznab.TorznabSource
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import animato.app.jellyfin.JellyfinSignInDialog
import animato.app.jellyfin.jellyfinServers
import animato.app.navigation.LensButton
import animato.app.source.SourceBrowseScreen
import animato.app.stremio.StremioAddonsScreen
import animato.app.stremio.installedAddons
import animato.app.stremio.m3uPlaylists
import animato.app.torznab.TorznabAddDialog
import animato.app.torznab.torznabIndexers
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.components.Pill
import animato.ui.theme.LocalAnimatoPalette
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.model.InstallStep
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Sources and extensions, for both halves, on one screen.
 *
 * Replaces two screens that looked nothing alike: Mihon's browse tabs for manga and a port of
 * Aniyomi's for anime, reached by flipping the lens. Neither had to say which half it was showing,
 * because each only ever showed one — which is why, on a device, there was no way to tell an anime
 * extension from a manga one.
 *
 * Installed and Available are **segments**, not chips: an underlined label, the same control the
 * title page uses for its tabs. Chips are the lens and category component, and two chip-shaped rows
 * on one screen is the thing the style guide bans outright.
 *
 * Repositories sits above the segments rather than inside Available, because a repository is where
 * content comes *from* and it governs both lists.
 */
class ExtensionsScreen : Screen() {

    @Composable
    override fun Content() = ExtensionsContent(canGoBack = true)
}

/**
 * The same screen whether it is pushed or is a destination.
 *
 * It became a destination because a device weighed it against what it replaced: *"Manage next to
 * Your sources is far too small for how important sources are — what if it took Downloads' place
 * in the bar?"* That is the right trade. A bar slot is worth having when it is somewhere you go
 * without a reason having appeared, and a download queue is the opposite of that: it is empty
 * unless something is being fetched, and it announces itself in the notification shade when it is
 * not. Sources is somewhere people go to look around.
 *
 * [canGoBack] is the only difference between the two lives: a destination has nothing to go back
 * to, and a back arrow that pops the whole tab host is worse than no arrow.
 */
@Composable
internal fun ExtensionsContent(canGoBack: Boolean = false) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = viewModel { ExtensionsScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableStateOf(ExtensionSegment.INSTALLED) }
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    val stremioStore = remember { Injekt.get<StremioAddonStore>() }
    val addons by stremioStore.addons.collectAsStateWithLifecycle()
    val playlistStore = remember { Injekt.get<M3uPlaylistStore>() }
    val playlists by playlistStore.playlists.collectAsStateWithLifecycle()
    val serverStore = remember { Injekt.get<JellyfinServerStore>() }
    val servers by serverStore.servers.collectAsStateWithLifecycle()
    var signInOpen by remember { mutableStateOf(false) }
    val indexerStore = remember { Injekt.get<TorznabIndexerStore>() }
    val indexers by indexerStore.indexers.collectAsStateWithLifecycle()
    var addIndexerOpen by remember { mutableStateOf(false) }

    // The merge a device asked for: an installed extension is somewhere you can GO, not just
    // a thing you manage. One source opens straight into its browse screen; several open a
    // picker first, because ten languages of one site are ten different sources.
    val openSource: (ExtensionRow, RowSource) -> Unit = { row, source ->
        navigator.push(SourceBrowseScreen(source.id, row.contentType))
    }

    if (languagesOpen) {
        LanguageSheet(
            languages = state.languages,
            onToggle = screenModel::toggleLanguage,
            onDismiss = { languagesOpen = false },
        )
    }

    if (addIndexerOpen) {
        TorznabAddDialog(
            store = indexerStore,
            onDismiss = { addIndexerOpen = false },
            onAdded = { addIndexerOpen = false },
        )
    }

    if (signInOpen) {
        JellyfinSignInDialog(
            store = serverStore,
            onDismiss = { signInOpen = false },
            // Nothing else to do on success: the store's flow is what the segment reads and what
            // the source manager watches, so the row and the source both appear on their own.
            onSignedIn = { signInOpen = false },
        )
    }

    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(AYMR.strings.label_sources_extensions)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                navigateUp = if (canGoBack) ({ navigator.pop() }) else null,
                scrollBehavior = scrollBehavior,
                actions = {
                    LensButton()
                    // Only once there is something to filter. On a first run the languages are
                    // whatever the repositories have not been asked for yet, and a control that
                    // opens an empty sheet is worse than one that is not there.
                    if (state.languages.isNotEmpty()) {
                        IconButton(onClick = { languagesOpen = true }) {
                            Icon(
                                imageVector = if (state.isLanguageFiltered) {
                                    Icons.Outlined.FilterAlt
                                } else {
                                    Icons.Outlined.FilterAltOff
                                },
                                contentDescription = stringResource(MR.strings.ext_info_language),
                                tint = if (state.isLanguageFiltered) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current
                                },
                            )
                        }
                    }
                    if (state.hasUpdates) {
                        TextButton(onClick = screenModel::updateAll) {
                            Text(stringResource(MR.strings.ext_update_all))
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

            /*
             * The segments are a pager, for the same reason the destinations are.
             *
             * The first version measured the drag and jumped once the finger lifted — a device
             * called it needing work, and it was the same complaint the tab bar had already
             * earned: nothing moves until you let go. A pager moves the lists with the finger and
             * settles where the fling points, and tapping a segment animates it there.
             *
             * The repositories row and the segments stay put above it. They govern both lists, so
             * sliding them out from under a swipe would be answering a question nobody asked.
             */
        // Addons and channels serve video and nothing else, so under the manga lens both segments
        // are furniture — and a tab that opens an empty list is worse than one that is absent.
        val videoSegments = setOf(ExtensionSegment.STREMIO, ExtensionSegment.IPTV, ExtensionSegment.SERVERS)
        val segments = ExtensionSegment.entries.filterNot {
            it in videoSegments && state.lens == ContentFilter.MANGA
        }
        val pagerState = rememberPagerState(initialPage = segments.indexOf(segment).coerceAtLeast(0)) {
            segments.size
        }

        LaunchedEffect(segment, segments) {
            val target = segments.indexOf(segment).coerceAtLeast(0)
            if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
        }
        LaunchedEffect(pagerState.settledPage) {
            val settled = segments.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
            if (segment != settled) segment = settled
        }

        Column(modifier = Modifier.padding(contentPadding)) {
            RepositoriesRow(
                storeCount = state.storeCount,
                navigator = navigator,
            )
            // Addons serve video and nothing else, so under the manga lens the segment is
            // furniture — and a tab that opens an empty list is worse than one that is absent.
            SegmentRow(selected = segment, segments = segments, onSelect = { segment = it })

            HorizontalPager(state = pagerState) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = MaterialTheme.padding.medium),
                ) {
                    when (segments[page]) {
                        ExtensionSegment.INSTALLED ->
                            if (state.installed.isEmpty()) {
                                item(key = "ships-empty") {
                                    ShipsEmptyState(onBrowse = { segment = ExtensionSegment.AVAILABLE })
                                }
                            } else {
                                extensionRows(state.installed, screenModel, openSource)
                            }

                        ExtensionSegment.STREMIO -> {
                            // What is installed, and nothing else. Adding lives behind *Extension
                            // stores* above, with the two extension repositories — see the note on
                            // installedAddons for what this segment used to do instead.
                            installedAddons(
                                addons = addons.filter { it.servesOnDemand },
                                onOpen = { addon ->
                                    navigator.push(
                                        SourceBrowseScreen(
                                            StremioSource.idFor(addon.url),
                                            ContentType.ANIME,
                                        ),
                                    )
                                },
                                onRemove = { url -> stremioStore.remove(url) },
                                onOpenStore = { navigator.push(StremioAddonsScreen()) },
                            )
                        }

                        ExtensionSegment.IPTV -> {
                            // Playlists first: for most people the M3U file *is* the IPTV source,
                            // and an addon that also carries channels is the rarer half.
                            m3uPlaylists(
                                playlists = playlists,
                                onOpen = { playlist ->
                                    navigator.push(
                                        SourceBrowseScreen(
                                            M3uSource.idFor(playlist.url),
                                            ContentType.ANIME,
                                        ),
                                    )
                                },
                                onRemove = { url -> playlistStore.remove(url) },
                            )
                            installedAddons(
                                addons = addons.filter { it.servesLiveTv },
                                // Only when there is nothing at all under this heading. With a
                                // playlist above, an empty-addons message would be describing a
                                // section rather than the screen.
                                showEmptyState = playlists.isEmpty(),
                                onOpen = { addon ->
                                    navigator.push(
                                        SourceBrowseScreen(
                                            StremioSource.idFor(addon.url),
                                            ContentType.ANIME,
                                        ),
                                    )
                                },
                                onRemove = { url -> stremioStore.remove(url) },
                                onOpenStore = {
                                    navigator.push(StremioAddonsScreen(liveTvOnly = true))
                                },
                            )
                        }

                        ExtensionSegment.SERVERS -> {
                            // Two kinds of thing under one heading, the way IPTV holds playlists
                            // and addons. Both are something the person runs themselves, both are
                            // added by typing an address, and neither has a directory to browse —
                            // which is what makes them one segment rather than two.
                            item(key = "servers-header") {
                                SourceSectionHeader(
                                    title = stringResource(AYMR.strings.servers_title),
                                    subtitle = stringResource(AYMR.strings.servers_summary),
                                )
                            }
                            jellyfinServers(
                                servers = servers,
                                onOpen = { server ->
                                    navigator.push(
                                        SourceBrowseScreen(
                                            JellyfinSource.idFor(server.url, server.userId),
                                            ContentType.ANIME,
                                        ),
                                    )
                                },
                                onRemove = { url -> serverStore.remove(url) },
                                onAdd = { signInOpen = true },
                            )
                            item(key = "indexers-header") {
                                SourceSectionHeader(
                                    title = stringResource(AYMR.strings.indexers_title),
                                    subtitle = stringResource(AYMR.strings.indexers_summary),
                                )
                            }
                            torznabIndexers(
                                indexers = indexers,
                                onOpen = { indexer ->
                                    navigator.push(
                                        SourceBrowseScreen(
                                            TorznabSource.idFor(indexer.url),
                                            ContentType.ANIME,
                                        ),
                                    )
                                },
                                onRemove = { url -> indexerStore.remove(url) },
                                onAdd = { addIndexerOpen = true },
                            )
                        }

                        ExtensionSegment.AVAILABLE ->
                            // Nothing on offer drew nothing at all: the segments, and then
                            // black. The two causes are both actionable and both named,
                            // because "no extensions" with five repositories configured is
                            // almost always the language filter.
                            if (state.available.isEmpty()) {
                                item(key = "available-empty") {
                                    AnimatoEmptyState(
                                        message = stringResource(AYMR.strings.extensions_none_available),
                                        actionLabel = stringResource(MR.strings.ext_info_language),
                                        onAction = { languagesOpen = true },
                                    )
                                }
                            } else {
                                state.available.forEach { group ->
                                    item(key = "lang-${group.languageCode}") {
                                        LanguageHeader(group.languageName)
                                    }
                                    extensionRows(group.rows, screenModel, openSource)
                                }
                            }
                    }
                }
            }
        }
    }
}

/**
 * A heading over one part of a segment that holds more than one kind of thing.
 *
 * Only the Servers segment uses it: media servers and indexers are both things the person runs
 * themselves and both belong under one chip, but they are not the same kind of thing and a list
 * that ran them together would read as one list of servers, half of which behave oddly.
 */
@Composable
private fun SourceSectionHeader(title: String, subtitle: String) {
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
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private enum class ExtensionSegment(val labelRes: StringResource) {
    INSTALLED(MR.strings.ext_installed),
    AVAILABLE(AYMR.strings.label_available),

    /**
     * The other kind of source, in the same row as the two kinds of extension.
     *
     * From a device: *"put Stremio next to installed and available, and let me go into them."* It
     * had a row above the segments that navigated away, which said "this is somewhere else" about
     * the one thing on this screen that is not somewhere else — it is a source, like everything
     * beside it, and opening one should land in its catalogue exactly as tapping an installed
     * extension does.
     */
    STREMIO(AYMR.strings.stremio_segment),

    /**
     * Live channels, beside the three kinds of on-demand source.
     *
     * Not a fourth mechanism: an IPTV addon is a Stremio addon whose declared type is `tv`, added
     * the same way through the same store. It is a heading of its own because *IPTV* is the word
     * somebody looking for television will look for, and a channel list buried inside a tab called
     * Stremio is findable only by people who already knew where it was.
     *
     * An addon that publishes films and channels both appears under both, which is true of it.
     */
    IPTV(AYMR.strings.iptv_segment),

    /**
     * The user's own machine, beside the strangers' websites.
     *
     * A Jellyfin or Emby server is a source like any other here, and the only thing that sets its
     * segment apart is that adding one happens on it: there is no directory of servers to browse,
     * because the only server anybody can add is one they already run.
     */
    SERVERS(AYMR.strings.servers_segment),
}

private fun LazyListScope.extensionRows(
    rows: List<ExtensionRow>,
    screenModel: ExtensionsScreenModel,
    onOpenSource: (ExtensionRow, RowSource) -> Unit,
) {
    items(items = rows, key = { it.key }) { row ->
        ExtensionListItem(
            row = row,
            onInstall = { screenModel.install(row) },
            onUpdate = { screenModel.update(row) },
            onUninstall = { screenModel.uninstall(row) },
            onTrust = { screenModel.trust(row) },
            onCancel = { screenModel.cancel(row) },
            onOpenSource = { source -> onOpenSource(row, source) },
        )
    }
}

/**
 * Where the extensions come from, above the lists they fill.
 *
 * Repositories really are per half — an anime store serves anime extensions and nothing else — so
 * under a narrowed lens the row goes straight to the one that applies. Under All it has to ask,
 * because there is no single list to open and quietly picking the manga one would hide the other
 * half from someone who has never seen this screen before. The count is the two added together,
 * which is the honest answer to "how many places is Animato fetching from".
 */
@Composable
private fun RepositoriesRow(
    storeCount: Int,
    navigator: Navigator,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            // Always the menu, never straight through to one of them.
            //
            // It used to follow the lens: manga went to the manga repositories, anime to the anime
            // ones, and only *All* offered a choice. That made where new sources come from depend
            // on a filter set somewhere else, and it left the third kind — Stremio — with nowhere
            // to be listed at all. Three kinds of place to get sources from, so three rows.
            modifier = Modifier.clickable { menuOpen = true },
            headlineContent = { Text(stringResource(MR.strings.extensionStores)) },
            supportingContent = {
                Text(pluralStringResource(MR.plurals.num_repos, storeCount, storeCount))
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.label_anime)) },
                onClick = {
                    menuOpen = false
                    navigator.push(AnimeExtensionStoresScreen())
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.label_manga)) },
                onClick = {
                    menuOpen = false
                    navigator.push(ExtensionStoresScreen())
                },
            )
            /*
             * The one that had no home.
             *
             * A Stremio addon is a source you add by address, so it belongs with the other two
             * answers to *where does Animato get things from* rather than hidden inside a tab of
             * what is already installed. It was reachable only from that tab's empty state, which
             * meant the way to add a second addon was to delete the first — the empty state was
             * the only thing that offered it.
             */
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.stremio_segment)) },
                onClick = {
                    menuOpen = false
                    navigator.push(StremioAddonsScreen())
                },
            )
            // The same store, opened on the channels. See StremioAddonsScreen for why that is a
            // filter rather than a second place to add things.
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.iptv_segment)) },
                onClick = {
                    menuOpen = false
                    navigator.push(StremioAddonsScreen(liveTvOnly = true))
                },
            )
        }
    }
}

/**
 * Installed · Available · Stremio, as an underlined label each.
 *
 * Not a chip row and not a TabRow: a chip row would collide with the lens and category chips used
 * everywhere else, and Material's TabRow insists on filling the width and carrying its own ripple,
 * which is a lot of furniture for two words.
 *
 * ## The width, which was wrong
 *
 * On a device, *Available* rendered as a narrow vertical strip with one letter per line. The
 * underline is a `Box` with `fillMaxWidth`, and inside a `Row` that means *the whole width the row
 * has left* — so the first segment's column took everything and the second was laid out in what
 * remained, which was a few dp. `IntrinsicSize.Min` measures each column against the widest thing in
 * it, which is the label, and the underline then fills that instead.
 */
@Composable
private fun SegmentRow(
    selected: ExtensionSegment,
    segments: List<ExtensionSegment>,
    onSelect: (ExtensionSegment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        segments.forEach { entry ->
            val active = entry == selected
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .clickable { onSelect(entry) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(entry.labelRes),
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SegmentUnderline)
                        .clip(RoundedCornerShape(SegmentUnderline))
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

/**
 * An extension's own logo, from wherever it is.
 *
 * A source icon is a logo rather than artwork, so it is 40 dp rather than the 48 dp an update thumb
 * gets, and it is never cropped — a logo trimmed to a square is a different logo.
 *
 * The placeholder is drawn behind the image rather than instead of it, so a row keeps its shape
 * while the icon loads and does not move when it arrives. It stays visible for an untrusted package,
 * which has no icon anybody should be reading.
 */
@Composable
private fun ExtensionIcon(icon: Any?) {
    Box(
        modifier = Modifier
            .size(SourceIconSize)
            .clip(RoundedCornerShape(SourceIconRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * Which languages this install is about.
 *
 * Not a temporary narrowing of a list: it writes the same preference Mihon's own browse screens
 * read, so turning Arabic on here turns it on everywhere and it is still on tomorrow. It lives on
 * this screen because this is where its absence was felt — the Available list defaults to the
 * device's language alone, and with nothing saying so a list of four extensions reads as a
 * repository with four extensions in it.
 */
@Composable
private fun LanguageSheet(
    languages: List<ExtensionLanguage>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(MR.strings.ext_info_language),
            modifier = Modifier.padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                bottom = MaterialTheme.padding.small,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyColumn {
            items(items = languages, key = { it.code }) { language ->
                ListItem(
                    modifier = Modifier.clickable { onToggle(language.code) },
                    headlineContent = { Text(language.name) },
                    leadingContent = {
                        Checkbox(
                            checked = language.enabled,
                            // The row is the control; a checkbox that also takes taps gives one
                            // choice two hit targets that report different things on a fast tap.
                            onCheckedChange = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageHeader(name: String) {
    Text(
        text = name,
        modifier = Modifier.padding(
            start = MaterialTheme.padding.medium,
            end = MaterialTheme.padding.medium,
            top = MaterialTheme.padding.medium,
            bottom = MaterialTheme.padding.extraSmall,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * One extension.
 *
 * The caption line carries everything that identifies it — the type chip, the language, the version
 * — plus the Update or NSFW mark, so no row grows a third control to hold them. The trailing slot
 * is one action and one menu, which is why Install is an *outlined* pill: on a first run the
 * screen's single filled button belongs to Browse available, and a list where every row has a
 * filled button has no primary at all.
 */
@Composable
private fun ExtensionListItem(
    row: ExtensionRow,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
    onOpenSource: (RowSource) -> Unit,
) {
    val palette = LocalAnimatoPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }

    Box {
        if (sourcesOpen) {
            DropdownMenu(expanded = true, onDismissRequest = { sourcesOpen = false }) {
                row.sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text("${source.name} · ${source.lang.uppercase()}") },
                        onClick = {
                            sourcesOpen = false
                            onOpenSource(source)
                        },
                    )
                }
            }
        }

        ListItem(
            modifier = Modifier.clickable(enabled = row.isInstalled && row.sources.isNotEmpty()) {
                if (row.sources.size == 1) {
                    onOpenSource(row.sources.first())
                } else {
                    sourcesOpen = true
                }
            },
            leadingContent = { ExtensionIcon(icon = row.icon) },
            headlineContent = {
                Text(text = row.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
            /*
             * A flow, not a row, because a row here crushes its own children.
             *
             * On a device the Update pill came out as an orange circle reading "Up", and the
             * install failure came out as one letter per line down the middle of the screen. A
             * `Row` gives each child the space it asks for in order and squeezes whatever is left
             * — so the version text took what it wanted and the marks after it got a few dp each.
             *
             * This is the third time this shape of fault has been found on this screen. A caption
             * carrying a variable number of marks has to be allowed a second line; the alternative
             * is a row that silently destroys the last thing added to it, every time.
             */
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    // On every row, whatever the lens says — this screen is the answer to "which of
                    // these is anime", so the answer cannot be conditional. It is the one exception to
                    // the rule that narrowing the lens removes type marks.
                    Pill(
                        text = stringResource(
                            when (row.contentType) {
                                ContentType.MANGA -> AYMR.strings.label_manga
                                ContentType.ANIME -> AYMR.strings.label_anime
                            },
                        ),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = listOf(row.lang.uppercase(), row.versionName)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.hasUpdate) {
                        // Warning rather than blue: an available update is attention, not novelty, and
                        // blue already means "active" everywhere else in the app.
                        Pill(
                            text = stringResource(MR.strings.ext_update),
                            containerColor = palette.warning,
                            contentColor = palette.ink,
                        )
                    }
                    if (row.isObsolete) {
                        Pill(
                            text = stringResource(MR.strings.ext_obsolete),
                            containerColor = palette.error,
                            contentColor = palette.ink,
                        )
                    }
                    if (row.isNsfw) {
                        // In words. Never an icon and never colour alone — someone has to be able to
                        // read what this row is rather than decode it.
                        Text(
                            text = stringResource(AYMR.strings.label_nsfw),
                            color = palette.warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                /*
                 * The reason, not just the fact. The design sheet's rule is that a failure states
                 * one, and "Install failed" states none — it is the same shrug as an empty screen
                 * saying "nothing here".
                 *
                 * Only one cause can be established from here, and when it is, it is the useful
                 * one: retrying a signature mismatch will fail forever, and the row says what to do
                 * instead. Everything else keeps the plain wording rather than inventing a
                 * diagnosis nobody checked.
                 */
                    if (row.installStep == InstallStep.Error) {
                        Text(
                            text = stringResource(
                                when (row.failure) {
                                    InstallFailure.DIFFERENT_REPOSITORY ->
                                        AYMR.strings.ext_install_failed_repo
                                    else -> AYMR.strings.ext_install_failed
                                },
                            ),
                            color = palette.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        row.installStep.isOngoing() -> TextButton(onClick = onCancel) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    /*
                     * A failed install said nothing at all, and that is how it was reported: "I
                     * update it and it still says update, no matter how many times." Which is
                     * exactly what a silent failure looks like from outside — the button is pressed,
                     * the install fails, the row goes back to offering the same update.
                     *
                     * Before the trust and update cases, so a failure is not hidden behind the very
                     * button that just failed.
                     */
                        row.installStep == InstallStep.Error -> OutlinedButton(
                            onClick = if (row.isInstalled) onUpdate else onInstall,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = LocalAnimatoPalette.current.error,
                            ),
                        ) {
                            Text(stringResource(MR.strings.action_retry))
                        }
                        row.isUntrusted -> OutlinedButton(onClick = onTrust) {
                            Text(stringResource(MR.strings.ext_trust))
                        }
                        row.hasUpdate -> OutlinedButton(onClick = onUpdate) {
                            Text(stringResource(MR.strings.ext_update))
                        }
                        !row.isInstalled -> OutlinedButton(onClick = onInstall) {
                            Text(stringResource(MR.strings.ext_install))
                        }
                    }
                    if (row.isInstalled) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.ext_uninstall)) },
                                    onClick = {
                                        menuOpen = false
                                        onUninstall()
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * The first run, explained rather than apologised for.
 *
 * An empty Installed list is not a failure and must not read as one: Animato ships with no sources
 * because it is not the one choosing where content comes from. Saying so plainly is also the only
 * honest thing to put here — a shrug would leave people hunting for a download that never failed.
 */
@Composable
private fun ShipsEmptyState(onBrowse: () -> Unit) {
    AnimatoEmptyState(
        message = stringResource(AYMR.strings.extensions_ships_empty),
        actionLabel = stringResource(AYMR.strings.action_browse_available),
        onAction = onBrowse,
    )
}

private fun InstallStep.isOngoing(): Boolean =
    this == InstallStep.Pending || this == InstallStep.Downloading || this == InstallStep.Installing

private val SourceIconSize = 40.dp
private val SourceIconRadius = 12.dp
private val SegmentUnderline = 2.dp
