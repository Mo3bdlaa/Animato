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
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import animato.app.navigation.LensButton
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
import tachiyomi.presentation.core.util.plus

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
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = viewModel { ExtensionsScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        var segment by rememberSaveable { mutableStateOf(ExtensionSegment.INSTALLED) }
        var languagesOpen by rememberSaveable { mutableStateOf(false) }

        if (languagesOpen) {
            LanguageSheet(
                languages = state.languages,
                onToggle = screenModel::toggleLanguage,
                onDismiss = { languagesOpen = false },
            )
        }

        Scaffold(
            topBar = { scrollBehavior ->
                SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(AYMR.strings.label_sources_extensions)) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::search,
                    navigateUp = navigator::pop,
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

            LazyColumn(contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium)) {
                item(key = "repositories") {
                    RepositoriesRow(
                        lens = state.lens,
                        storeCount = state.storeCount,
                        navigator = navigator,
                    )
                }

                item(key = "segments") {
                    SegmentRow(selected = segment, onSelect = { segment = it })
                }

                when (segment) {
                    ExtensionSegment.INSTALLED ->
                        if (state.installed.isEmpty()) {
                            item(key = "ships-empty") {
                                ShipsEmptyState(onBrowse = { segment = ExtensionSegment.AVAILABLE })
                            }
                        } else {
                            extensionRows(state.installed, screenModel)
                        }

                    ExtensionSegment.AVAILABLE ->
                        // Nothing on offer drew nothing at all: the segments, and then black. The
                        // two causes are both actionable and both named, because "no extensions"
                        // with five repositories configured is almost always the language filter.
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
                                item(key = "lang-${group.languageCode}") { LanguageHeader(group.languageName) }
                                extensionRows(group.rows, screenModel)
                            }
                        }
                }
            }
        }
    }
}

private enum class ExtensionSegment(val labelRes: StringResource) {
    INSTALLED(MR.strings.ext_installed),
    AVAILABLE(AYMR.strings.label_available),
}

private fun LazyListScope.extensionRows(
    rows: List<ExtensionRow>,
    screenModel: ExtensionsScreenModel,
) {
    items(items = rows, key = { it.key }) { row ->
        ExtensionListItem(
            row = row,
            onInstall = { screenModel.install(row) },
            onUpdate = { screenModel.update(row) },
            onUninstall = { screenModel.uninstall(row) },
            onTrust = { screenModel.trust(row) },
            onCancel = { screenModel.cancel(row) },
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
    lens: ContentFilter,
    storeCount: Int,
    navigator: Navigator,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.clickable {
                when (lens) {
                    ContentFilter.MANGA -> navigator.push(ExtensionStoresScreen())
                    ContentFilter.ANIME -> navigator.push(AnimeExtensionStoresScreen())
                    ContentFilter.ALL -> menuOpen = true
                }
            },
            headlineContent = { Text(stringResource(MR.strings.extensionStores)) },
            supportingContent = {
                Text(pluralStringResource(MR.plurals.num_repos, storeCount, storeCount))
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.label_manga)) },
                onClick = {
                    menuOpen = false
                    navigator.push(ExtensionStoresScreen())
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AYMR.strings.label_anime)) },
                onClick = {
                    menuOpen = false
                    navigator.push(AnimeExtensionStoresScreen())
                },
            )
        }
    }
}

/**
 * Installed · Available, as an underlined label each.
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
    onSelect: (ExtensionSegment) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.large),
    ) {
        ExtensionSegment.entries.forEach { entry ->
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
) {
    val palette = LocalAnimatoPalette.current
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
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
