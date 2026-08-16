package animato.app.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import animato.app.extension.ExtensionsScreen
import animato.app.navigation.LensGlyph
import animato.app.settings.AnimatoSettingsScreen
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Six steps from a brand moment to a Discover that already has things in it.
 *
 * ## Why this replaces Mihon's
 *
 * Mihon's onboarding is four steps about the device — theme, storage, permissions, links to guides.
 * All useful, none of it about what the app *is*, and none of it aware that this one has two halves.
 * Somebody arriving here has to learn one idea before anything else makes sense — the lens — and the
 * fastest way to teach a control is to make the first decision with it. Step two's three options are
 * drawn with the real lens glyph, so the icon in the top bar is already familiar when it appears.
 *
 * ## What was kept from Mihon's, and where it went
 *
 * The install-packages permission is the one that matters: without it no extension can be
 * installed, so it sits on step four, which is the step about where content comes from. That is a
 * better home than a generic permissions page — you are being asked for it at the moment it is
 * about to be needed. The notification permission sits on the last step, next to the sentence about
 * updates arriving.
 *
 * Storage location is deliberately not here. `StoragePreferences` defaults to a real directory the
 * app can write to, so the step existed to offer a change rather than to make something work, and
 * it lives in Settings › Downloads & storage where a change belongs.
 *
 * ## The legal position, on step four
 *
 * Official portals are listed **by name only**, with no install control and no pre-filled
 * repository. Nothing is bundled. The one deviation from the design sheet is that adding a
 * repository sends you to Sources & extensions rather than offering a paste field here: a
 * repository serves one half, and a single field on this screen could not know which, so it would
 * have had to guess. The screen it sends you to asks.
 */
class AnimatoOnboardingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val contentPreferences = remember { Injekt.get<ContentPreferences>() }
        val sourcePreferences = remember { Injekt.get<SourcePreferences>() }

        var step by remember { mutableStateOf(0) }

        val finish: () -> Unit = {
            basePreferences.shownOnboardingFlow.set(true)
            navigator.pop()
        }

        // Leaving before the end would drop someone into an app whose central control they have
        // never seen. Skip is on every step and is the way out.
        BackHandler(enabled = true) {
            if (step > 0) step-- else Unit
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopBarHeight)
                    .padding(horizontal = Padding),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // On every step including the first. A flow that promises skippable and hides the
                // control on step one has broken the promise before it starts.
                TextButton(onClick = finish) {
                    Text(stringResource(MR.strings.onboarding_action_skip))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Padding),
            ) {
                when (step) {
                    0 -> BrandStep()
                    1 -> LensStep(contentPreferences)
                    2 -> LanguagesStep(sourcePreferences)
                    3 -> SourcesStep()
                    4 -> HistoryStep(
                        // Both land in Settings, which is where the file picker and the tracker
                        // logins already live. Duplicating either here would be a second copy of a
                        // screen that has to keep working anyway.
                        onImport = {
                            finish()
                            navigator.push(AnimatoSettingsScreen())
                        },
                        onTracker = {
                            finish()
                            navigator.push(AnimatoSettingsScreen())
                        },
                        onFresh = { step = 5 },
                    )
                    else -> DoneStep()
                }
            }

            Column(
                modifier = Modifier.padding(Padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Padding),
            ) {
                StepIndicator(current = step, total = STEPS)
                // Step five has no primary: its three cards are the choice, and a Continue beneath
                // them would make "which of these" and "go" compete for the same tap.
                if (step != 4) {
                    Button(
                        onClick = { if (step == STEPS - 1) finish() else step++ },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                when (step) {
                                    0 -> AYMR.strings.onboarding_get_started
                                    STEPS - 1 -> AYMR.strings.onboarding_start_exploring
                                    else -> AYMR.strings.onboarding_action_next
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** The brand moment. Manga DNA is loud here and on the last step, and silent on the four between. */
@Composable
private fun BrandStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Padding),
    ) {
        Spacer(modifier = Modifier.height(BrandTopSpace))
        Text(
            text = stringResource(MR.strings.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(AYMR.strings.onboarding_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The lens, taught with the lens.
 *
 * The three options are drawn with the real glyph rather than with words or with icons invented for
 * this screen, so the control is learned by using it once rather than decoded later in a top bar.
 */
@Composable
private fun LensStep(contentPreferences: ContentPreferences) {
    var selected by remember { mutableStateOf(contentPreferences.contentFilter.get()) }

    StepBody(
        title = stringResource(AYMR.strings.onboarding_lens_title),
        subtitle = stringResource(AYMR.strings.onboarding_lens_subtitle),
    ) {
        ContentFilter.entries.forEach { option ->
            OptionRow(
                selected = option == selected,
                onClick = {
                    selected = option
                    contentPreferences.contentFilter.set(option)
                },
                leading = { LensGlyph(lens = option) },
                title = stringResource(
                    when (option) {
                        ContentFilter.ANIME -> AYMR.strings.label_anime
                        ContentFilter.MANGA -> AYMR.strings.label_manga
                        ContentFilter.ALL -> AYMR.strings.onboarding_lens_both
                    },
                ),
                subtitle = stringResource(
                    when (option) {
                        ContentFilter.ANIME -> AYMR.strings.onboarding_lens_anime_hint
                        ContentFilter.MANGA -> AYMR.strings.onboarding_lens_manga_hint
                        ContentFilter.ALL -> AYMR.strings.onboarding_lens_both_hint
                    },
                ),
            )
        }
    }
}

/**
 * Content languages, Arabic first.
 *
 * Alphabetical order would bury Arabic under a dozen European languages, and this is the one screen
 * where that ordering decides whether an Arabic reader believes the app is for them. Each language
 * is written in its own script, because a list of endonyms is readable by exactly the people each
 * row is addressed to.
 */
@Composable
private fun LanguagesStep(sourcePreferences: SourcePreferences) {
    var enabled by remember { mutableStateOf(sourcePreferences.enabledLanguages.get()) }

    StepBody(
        title = stringResource(AYMR.strings.onboarding_languages_title),
        subtitle = stringResource(AYMR.strings.onboarding_languages_subtitle),
    ) {
        LANGUAGES.forEach { (code, name) ->
            val checked = code in enabled
            OptionRow(
                selected = checked,
                onClick = {
                    enabled = if (checked) enabled - code else enabled + code
                    sourcePreferences.enabledLanguages.set(enabled)
                },
                leading = { Checkbox(checked = checked, onCheckedChange = null) },
                title = name,
                subtitle = null,
            )
        }
    }
}

/**
 * Where content comes from, said plainly.
 *
 * Portals are named and nothing else: no install button, no pre-filled repository, nothing bundled.
 * The install permission is asked for here because this is the step where it is about to matter.
 */
@Composable
private fun SourcesStep() {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val installGranted = rememberRequestPackageInstallsPermissionState()

    StepBody(
        title = stringResource(AYMR.strings.onboarding_sources_title),
        subtitle = stringResource(AYMR.strings.extensions_ships_empty),
    ) {
        Text(
            text = stringResource(AYMR.strings.onboarding_portals_header),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        PORTALS.forEach { (name, note) ->
            Column(modifier = Modifier.padding(vertical = SmallPadding)) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!installGranted) {
            OptionRow(
                selected = false,
                onClick = { context.launchRequestPackageInstallsPermission() },
                leading = null,
                title = stringResource(MR.strings.ext_permission_install_apps_warning),
                subtitle = null,
            )
        }

        OptionRow(
            selected = false,
            // A repository serves one half, and a paste field here could not know which. The
            // Sources screen asks, so the trip there is the honest version of the design's field.
            onClick = { navigator.push(ExtensionsScreen()) },
            leading = null,
            title = stringResource(MR.strings.extensionStores),
            subtitle = stringResource(AYMR.strings.onboarding_sources_later),
        )
    }
}

/** Three cards and no primary button: the cards are the choice. */
@Composable
private fun HistoryStep(
    onImport: () -> Unit,
    onTracker: () -> Unit,
    onFresh: () -> Unit,
) {
    StepBody(
        title = stringResource(AYMR.strings.onboarding_history_title),
        subtitle = stringResource(AYMR.strings.onboarding_history_subtitle),
    ) {
        OptionRow(
            selected = false,
            onClick = onImport,
            leading = null,
            title = stringResource(AYMR.strings.aniyomi_import),
            subtitle = stringResource(AYMR.strings.onboarding_history_import),
        )
        OptionRow(
            selected = false,
            onClick = onTracker,
            leading = null,
            title = stringResource(AYMR.strings.onboarding_history_tracker),
            subtitle = stringResource(AYMR.strings.onboarding_history_tracker_hint),
        )
        // A card and not a text link: the honest option must not be the quiet one.
        OptionRow(
            selected = false,
            onClick = onFresh,
            leading = null,
            title = stringResource(AYMR.strings.onboarding_history_fresh),
            subtitle = stringResource(AYMR.strings.onboarding_history_fresh_hint),
        )
    }
}

/** The last thing read before Discover, which by then already has three rails in it. */
@Composable
private fun DoneStep() {
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Padding),
    ) {
        Spacer(modifier = Modifier.height(BrandTopSpace))
        Text(
            text = stringResource(AYMR.strings.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(AYMR.strings.onboarding_done_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepBody(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SmallPadding)) {
        Spacer(modifier = Modifier.height(SmallPadding))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(SmallPadding))
        content()
    }
}

/**
 * One 72 dp choice.
 *
 * The same row whether it is picking one thing, ticking several, or opening somewhere else, because
 * they all read as *a thing you tap on this screen* and giving each its own shape would make the
 * flow look like six unrelated forms.
 */
@Composable
private fun OptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)?,
    title: String,
    subtitle: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OptionRowHeight)
            .clip(RoundedCornerShape(OptionRadius))
            .border(
                width = BorderWidth,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(OptionRadius),
            )
            .clickable(onClick = onClick)
            .padding(Padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Padding),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Six dots, the current one a pill. Position, not progress — every step here can be skipped. */
@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(DotGap)) {
        repeat(total) { index ->
            val active = index == current
            Box(
                modifier = Modifier
                    .size(width = if (active) ActiveDotWidth else DotSize, height = DotSize)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

/**
 * Portals named and not offered.
 *
 * These are official, licensed places to read and watch, and they are here as information: nobody
 * is installing anything from this list, and Animato is not the one deciding where anybody's
 * content comes from. That is the whole legal position in one screen.
 */
private val PORTALS = listOf(
    "MANGA Plus" to "Shueisha · free chapters",
    "Manga Arabia" to "Arabic · licensed",
    "Jellyfin" to "Your own server",
)

/** Arabic first, then the languages with the most extensions behind them, each in its own script. */
private val LANGUAGES = listOf(
    "ar" to "العربية",
    "en" to "English",
    "ja" to "日本語",
    "ko" to "한국어",
    "fr" to "Français",
    "es" to "Español",
    "id" to "Indonesia",
)

private const val STEPS = 6

private val Padding = 16.dp
private val SmallPadding = 8.dp
private val TopBarHeight = 56.dp
private val BrandTopSpace = 96.dp
private val OptionRowHeight = 72.dp
private val OptionRadius = 12.dp
private val BorderWidth = 1.dp
private val DotSize = 6.dp
private val ActiveDotWidth = 16.dp
private val DotGap = 8.dp
