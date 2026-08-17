package animato.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import animato.domain.content.ContentType
import animato.ui.navigation.AnimatoNavigator
import animato.ui.navigation.AnimatoRoot
import animato.ui.navigation.AnimatoTab
import aniyomi.domain.library.service.AnimeLibraryPreferences
import aniyomi.domain.source.service.AnimeSourcePreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.util.isTabletUi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The root of the app: five destinations, and the screens under them.
 *
 * This replaces Mihon's `HomeScreen` rather than extending it — its tab list is private to the
 * object, so a different bar means a different host. What is *not* different is how the host works:
 * the Voyager tab navigator, the fade between tabs, the rail on tablets and the badge logic are
 * Mihon's, because they were right and copying them costs one file of drift instead of a bar that
 * behaves unlike the rest of the app.
 *
 * The bar itself is Animato's: History folds into Home's continue rail, Browse becomes Discover,
 * Downloads is promoted out of More, and More is gone — settings are reached from Home. See
 * `docs/BRANDING.md`.
 */
object AnimatoHomeScreen : Screen(), AnimatoRoot {

    @Suppress("ConstPropertyName")
    private const val TabFadeDuration = 200

    @Suppress("ConstPropertyName")
    private const val TabNavigatorKey = "AnimatoTabs"

    private val SwipeDistanceThreshold = 96.dp

    /** Pixels per second. A real fling, not the tail end of a hesitant drag. */
    @Suppress("ConstPropertyName")
    private const val SwipeVelocityThreshold = 1100f

    private val TABS = listOf(
        AnimatoHomeTab,
        AnimatoLibraryTab,
        AnimatoDiscoverTab,
        AnimatoUpdatesTab,
        AnimatoDownloadsTab,
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        TabNavigator(
            tab = AnimatoHomeTab,
            key = TabNavigatorKey,
        ) { tabNavigator ->
            val bottomNavVisible by produceState(initialValue = true) {
                AnimatoNavigator.bottomNavVisibility.collectLatest { value = it }
            }
            // Provide usable navigator to content screen
            CompositionLocalProvider(LocalNavigator provides navigator) {
                Scaffold(
                    startBar = {
                        if (isTabletUi()) {
                            NavigationRail {
                                TABS.fastForEach {
                                    NavigationRailItem(it)
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (!isTabletUi()) {
                            AnimatedVisibility(
                                visible = bottomNavVisible,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                NavigationBar {
                                    TABS.fastForEach {
                                        NavigationBarItem(it)
                                    }
                                }
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets(0),
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding)
                            .destinationSwipe(
                                // Only while the bar is visible: it disappears exactly when a
                                // screen is in a mode — selection — where silently switching
                                // destinations would discard what the user is doing.
                                enabled = bottomNavVisible,
                                onSwitch = { step ->
                                    val index = TABS.indexOfFirst { it::class == tabNavigator.current::class }
                                    TABS.getOrNull(index + step)?.let { tabNavigator.current = it }
                                },
                            ),
                    ) {
                        AnimatedContent(
                            targetState = tabNavigator.current,
                            transitionSpec = {
                                materialFadeThroughIn(initialScale = 1f, durationMillis = TabFadeDuration) togetherWith
                                    materialFadeThroughOut(durationMillis = TabFadeDuration)
                            },
                            label = "tabContent",
                        ) {
                            tabNavigator.saveableState(key = "currentTab", it) {
                                it.Content()
                            }
                        }
                    }
                }
            }

            val goToHomeTab = { tabNavigator.current = AnimatoHomeTab }

            BackHandler(enabled = tabNavigator.current != AnimatoHomeTab, onBack = goToHomeTab)

            LaunchedEffect(Unit) {
                AnimatoNavigator.tabRequests.collectLatest {
                    tabNavigator.current = when (it) {
                        AnimatoTab.HOME -> AnimatoHomeTab
                        AnimatoTab.LIBRARY -> AnimatoLibraryTab
                        AnimatoTab.DISCOVER -> AnimatoDiscoverTab
                        AnimatoTab.UPDATES -> AnimatoUpdatesTab
                        AnimatoTab.DOWNLOADS -> AnimatoDownloadsTab
                    }
                }
            }
        }
    }

    /**
     * A horizontal swipe on a destination moves to the neighbouring one — but only a swipe nothing
     * else wanted.
     *
     * Asked for from a device in exactly those terms: *"when I swipe sideways and there is no
     * swipe thing there, I want to move between home/library/discover/updates."* The "no swipe
     * thing" clause is not extra work, it is how Compose already dispatches: a rail, a pager or a
     * carousel under the finger consumes the horizontal drag, and a parent [draggable] only ever
     * sees what no child claimed. So this sits on the box that hosts every destination and is
     * inert wherever the content itself scrolls sideways.
     *
     * Direction follows reading direction: the gesture is "pull the next destination in from the
     * end", which in RTL — this app is used in Arabic — is the mirror of LTR. A swipe either
     * travels far enough or arrives fast enough; a slow half-hearted drag does nothing.
     */
    @Composable
    private fun Modifier.destinationSwipe(
        enabled: Boolean,
        onSwitch: (Int) -> Unit,
    ): Modifier {
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val distanceThreshold = with(LocalDensity.current) { SwipeDistanceThreshold.toPx() }
        var dragTotal by remember { mutableFloatStateOf(0f) }
        return this.draggable(
            orientation = Orientation.Horizontal,
            enabled = enabled,
            state = rememberDraggableState { delta -> dragTotal += delta },
            onDragStarted = { dragTotal = 0f },
            onDragStopped = { velocity ->
                val toStart = if (isRtl) -dragTotal else dragTotal
                val toStartVelocity = if (isRtl) -velocity else velocity
                when {
                    toStart < -distanceThreshold || toStartVelocity < -SwipeVelocityThreshold -> onSwitch(1)
                    toStart > distanceThreshold || toStartVelocity > SwipeVelocityThreshold -> onSwitch(-1)
                }
            },
        )
    }

    @Composable
    private fun RowScope.NavigationBarItem(tab: Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationBarItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    @Composable
    private fun NavigationRailItem(tab: Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        NavigationRailItem(
            selected = selected,
            onClick = {
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
        )
    }

    /**
     * The counts on Updates and Discover, for whichever library is in view.
     *
     * Both libraries keep their own counters, so the badge follows the lens rather than adding them
     * together — a number that mixed unread chapters into unseen episodes would not mean anything.
     */
    @Composable
    private fun NavigationIconItem(tab: Tab) {
        val contentType = contentTypeOrDefault()
        BadgedBox(
            badge = {
                when (tab) {
                    is AnimatoUpdatesTab -> {
                        val count by produceState(initialValue = 0, contentType) {
                            when (contentType) {
                                ContentType.MANGA -> {
                                    val pref = Injekt.get<LibraryPreferences>()
                                    combine(
                                        pref.newShowUpdatesCount.changes(),
                                        pref.newUpdatesCount.changes(),
                                    ) { show, count -> if (show) count else 0 }
                                        .collectLatest { value = it }
                                }
                                ContentType.ANIME -> {
                                    Injekt.get<AnimeLibraryPreferences>().newAnimeUpdatesCount().changes()
                                        .collectLatest { value = it }
                                }
                            }
                        }
                        CountBadge(count, MR.plurals.notification_chapters_generic)
                    }
                    is AnimatoDiscoverTab -> {
                        val count by produceState(initialValue = 0, contentType) {
                            when (contentType) {
                                ContentType.MANGA ->
                                    Injekt.get<SourcePreferences>().extensionUpdatesCount.changes()
                                        .collectLatest { value = it }
                                ContentType.ANIME ->
                                    Injekt.get<AnimeSourcePreferences>().animeExtensionUpdatesCount.changes()
                                        .collectLatest { value = it }
                            }
                        }
                        CountBadge(count, MR.plurals.update_check_notification_ext_updates)
                    }
                    else -> {}
                }
            },
        ) {
            Icon(
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
            )
        }
    }

    @Composable
    private fun CountBadge(count: Int, description: dev.icerock.moko.resources.PluralsResource) {
        if (count <= 0) return
        Badge {
            val desc = pluralStringResource(description, count = count, count)
            Text(
                text = count.toString(),
                modifier = Modifier.semantics { contentDescription = desc },
            )
        }
    }
}
