package animato.app.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.presentation.core.components.TwoPanelBox
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

/**
 * The settings root, with the anime section on it.
 *
 * Mihon's `SettingsScreen` hosts its own `SettingsMainScreen`, and both are Mihon's files, so there
 * is no way to add a row to that list. The list is a list of rows and cheap to own: this is the same
 * host, pointed at [AnimatoSettingsMainScreen], which is Mihon's list plus one entry.
 *
 * The cost is one file that has to notice when Mihon adds a settings section. That is the whole
 * cost, and it is smaller than the alternative — Aniyomi's, which was to edit five of Mihon's
 * settings screens and make every future update of them a conflict.
 *
 * ## Two layouts
 *
 * On a phone the list is the screen and a section is pushed on top of it. On a tablet both are on
 * screen at once — list on the left, section on the right — because a settings list is short and a
 * tablet's width would otherwise go to margins. Only the wide layout has a *selected* row, since it
 * is the only one where a row and the thing it opened are visible together.
 *
 * The right half starts on Appearance rather than on nothing. A blank pane beside a full list reads
 * as a screen that failed to load, and there is no useful empty state for "pick something" when the
 * something is already listed beside it.
 */
class AnimatoSettingsScreen(
    /**
     * Where the settings open, when the caller has somewhere in mind.
     *
     * The default is the list, which is what a *Settings* button means. Somewhere that already
     * knows the section — the tracking hub's *Sign in*, whose whole purpose is the tracking
     * screen — passes it and skips the list, and Back still returns to the caller rather than to a
     * settings root that was never visited.
     */
    private val startAt: VoyagerScreen = AnimatoSettingsMainScreen,
) : Screen() {

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow
        if (!isTabletUi()) {
            Navigator(
                screen = startAt,
                onBackPressed = null,
            ) { navigator ->
                val pop: () -> Unit = {
                    if (navigator.canPop) navigator.pop() else parentNavigator.pop()
                }
                CompositionLocalProvider(LocalBackPress provides pop) {
                    DefaultNavigatorScreenTransition(navigator = navigator)
                }
            }
            return
        }

        Navigator(
            // The list is the left half here, so it is never also the right one; a caller that
            // named a section still gets it, and everyone else lands on Appearance.
            screen = startAt.takeIf { it != AnimatoSettingsMainScreen } ?: SettingsAppearanceScreen,
            onBackPressed = null,
        ) { navigator ->
            val insets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
            TwoPanelBox(
                modifier = Modifier
                    .windowInsetsPadding(insets)
                    .consumeWindowInsets(insets),
                startContent = {
                    // Back on the left half leaves settings altogether: there is nothing above the
                    // list to go back to when the list has been on screen the whole time.
                    CompositionLocalProvider(LocalBackPress provides parentNavigator::pop) {
                        AnimatoSettingsMainScreen.Content(twoPane = true)
                    }
                },
                endContent = { DefaultNavigatorScreenTransition(navigator = navigator) },
            )
        }
    }
}
