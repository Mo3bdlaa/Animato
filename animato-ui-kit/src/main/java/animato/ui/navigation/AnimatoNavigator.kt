package animato.ui.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The five destinations of the Animato tab bar.
 *
 * Mihon's are Library, Updates, History, Browse and More. Animato's fold History into Home's
 * continue rail, rename Browse to Discover because discovery is by content rather than by source,
 * promote Downloads out of More, and drop More itself — settings are reached from Home rather than
 * costing a fifth of the bar. See `docs/BRANDING.md`.
 */
enum class AnimatoTab {
    HOME,
    LIBRARY,
    DISCOVER,
    UPDATES,
    DOWNLOADS,
}

/**
 * How a screen asks the tab bar to do something.
 *
 * The tab host lives in the application module, which is the only module that can see both
 * libraries' screens. The screens themselves live in modules below it and cannot see the host, so
 * they talk to it through this object rather than by holding a reference to it.
 *
 * Mihon's `HomeScreen` does the same thing with the same shape, and its channels are private to it,
 * which is why this exists rather than being reused: nothing outside Mihon's own file can receive
 * from them.
 *
 * Both channels drop the oldest value rather than suspending. A rendezvous channel would suspend
 * the caller forever whenever the host is not mounted — during a configuration change, or before
 * the first composition — and these are notifications, not handshakes: the newest value is the only
 * one that matters.
 */
object AnimatoNavigator {

    private val bottomNavEvents = Channel<Boolean>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val tabEvents = Channel<AnimatoTab>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val bottomNavVisibility: Flow<Boolean> = bottomNavEvents.receiveAsFlow()

    val tabRequests: Flow<AnimatoTab> = tabEvents.receiveAsFlow()

    /** Hides the tab bar, so a selection-mode action bar can take its place. */
    fun showBottomNav(show: Boolean) {
        bottomNavEvents.trySend(show)
    }

    fun openTab(tab: AnimatoTab) {
        tabEvents.trySend(tab)
    }
}

/**
 * Marks the screen that hosts the tab bar.
 *
 * A screen that wants to know "did I get here from the tab bar, or from a source listing?" — the
 * anime details screen asks exactly that before deciding where a genre search should go — cannot
 * name the host, because the host lives in the application module above it. This is the seam.
 */
interface AnimatoRoot
