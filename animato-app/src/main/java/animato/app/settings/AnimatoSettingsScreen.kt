package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
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
 * Only the phone layout is here. Mihon's screen also has a two-pane tablet layout with its own
 * navigator; that is worth having and is not worth copying twice, so it comes when the settings get
 * their own tablet pass.
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
    }
}
