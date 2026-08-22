package animato.app

import android.animation.ValueAnimator
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import animato.anime.backup.create.AnimatoBackupCreateJob
import animato.anime.services.AnimeConstants
import animato.anime.services.AnimeNotifications
import animato.anime.ui.stores.AnimeExtensionStoresScreen
import animato.app.coil.AnimatoImageLoader
import animato.app.crash.CrashRecorder
import animato.app.crash.CrashReportPrompt
import animato.app.downloads.DownloadCleanupPreferences
import animato.app.downloads.OrphanedDownloadSweeper
import animato.app.entry.EntryScreen
import animato.app.extension.ExtensionUpdateCheck
import animato.app.navigation.AnimatoHomeScreen
import animato.app.navigation.setContentLens
import animato.app.nsfw.NsfwDefaults
import animato.app.onboarding.AnimatoOnboardingScreen
import animato.app.settings.AniyomiImportScreen
import animato.app.source.SourceBrowseScreen
import animato.app.sync.LibrarySyncJob
import animato.app.updater.AnimatoAppUpdateChecker
import animato.di.AnimeInjekt
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.ui.deeplink.DeepLinkScreenType
import animato.ui.navigation.AnimatoNavigator
import animato.ui.navigation.AnimatoTab
import animato.ui.theme.AnimatoTheme
import animato.ui.tv.ProvideIsTelevision
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreen
import eu.kanade.tachiyomi.ui.deeplink.anime.DeepLinkAnimeScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.migration.Migrator
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy

/**
 * Animato's entry point, and the reason phase 6 exists.
 *
 * This is an adapted copy of Mihon's `MainActivity`, which is the one thing the architecture's
 * "never open a file that belongs to Mihon" rule cannot express as a seam. Mihon picks its colours
 * from an `AppTheme` **enum** through a **private** `colorSchemes` map, backed by an `internal`
 * `BaseColorScheme`. An enum cannot be extended from another module and a private map cannot be
 * added to, so there is no way to register the Animato palette with Mihon's theme.
 *
 * What there is instead: Mihon's 186 presentation files hold only 4 hard-coded colours between them
 * and read `MaterialTheme.colorScheme` for the rest. So the palette is applied *above* Mihon's
 * screens rather than inside their theme, and whoever owns the root composable restyles all of them
 * at once. Owning this activity is what buys that.
 *
 * The root it hosts is [animato.app.navigation.AnimatoHomeScreen], Animato's own five-destination
 * bar, rather than Mihon's `HomeScreen`.
 *
 * ## What differs from Mihon's version
 *
 * **The update check is ours.** `AppUpdateChecker` reads releases from `mihonapp/mihon`, and
 * calling it here would offer Animato's users Mihon's APK — which they could not install even if
 * they wanted to, since it is a different package signed with a different key. [CheckForUpdates]
 * asks this repository instead; see [AnimatoAppUpdateChecker] for why pointing Mihon's at us was
 * not enough. The screen it pushes is still Mihon's.
 *
 * **The donation campaign is absent.** It pushes Mihon's `SupportUsScreen`. Soliciting donations to
 * another project from a rebranded app is not ours to do.
 *
 * Both are listed in UPSTREAM_DIVERGENCE.md, because a future sync will show them as changed rather
 * than as decided.
 */
class MainActivity : BaseActivity() {

    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val downloadCleanupPreferences: DownloadCleanupPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    private val downloadCache: DownloadCache by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()

    private val getIncognitoState: GetIncognitoState by injectLazy()

    /**
     * Dismisses the splash screen once there is something to show.
     *
     * Mihon's tabs set this themselves, via `(context as? MainActivity)?.ready = true` — a cast
     * against *their* activity class, which this one is not and cannot be, since their
     * `MainActivity` is final. Left alone the cast would silently fail and the splash would sit
     * there for its full five-second ceiling on every cold start.
     *
     * Owning the activity means owning the flag too, so it is set when the navigator first
     * composes. The trade is that the splash leaves at the 500 ms floor and a tab may paint its
     * loading state for a moment, rather than the splash covering that moment. A brief spinner
     * beats a five-second freeze.
     */
    var ready = false

    private var navigator: Navigator? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        /*
         * Before anything asks Injekt for an anime type — which the image loader on the next line
         * does, by way of its cover keyer.
         *
         * AnimeInjektInitializer normally has this done already. Normally: it schedules the
         * registration during application bind, and for one release that schedule could lose to
         * the activity launch sitting in the same queue. The result was a crash before the first
         * frame, with a stack that named the cover keyer and no way to guess why it only sometimes
         * happened. The initializer has been fixed; this line is why it can never matter again.
         *
         * Idempotent by scope identity, so on every launch where the initializer won, this is one
         * reference comparison.
         */
        AnimeInjekt.ensureRegistered(application)

        // Over the top of Mihon's handler and chained to it, so the crash screen still appears and
        // the trace behind it survives being dismissed. See CrashRecorder for why this is a file
        // on the device rather than a report sent anywhere.
        CrashRecorder.install(application)

        // Before a frame is drawn, because it is what makes an anime cover loadable at all. See
        // AnimatoImageLoader for why it extends Mihon's loader instead of building one.
        AnimatoImageLoader.install(this)

        Migrator.awaitAndRelease()

        /*
         * Claim the periodic backup slot, now that the migrations have finished.
         *
         * One of Mihon's migrations schedules Mihon's backup job under the same name, and that job
         * writes manga only — on this app, half a backup. Doing it here rather than earlier is the
         * point of doing it at all: `awaitAndRelease` above has already run every migration, so
         * this is the last word rather than a race with one.
         */
        AnimatoBackupCreateJob.setupTask(this)
        // Beside the backup job for the same reason it is here: WorkManager forgets periodic work
        // across an app update on some devices, and re-enqueueing on every launch is idempotent.
        LibrarySyncJob.setupTask(this)

        /*
         * NSFW hidden unless somebody chose otherwise, and anything adult incognito by default —
         * extensions by their own flag, Stremio addons by the directory's. See NsfwDefaults for
         * why all three are seeded data rather than changed defaults.
         *
         * Each one is walled off, because none of them is worth an app that will not start. That
         * is not hypothetical: the addon seeding shipped asking Injekt for something never
         * registered, and an exception in a coroutine launched from `onCreate` is an exception in
         * `onCreate` — the app died on the splash screen, every launch, with no way back in from
         * the device. Defaults are housekeeping; housekeeping fails quietly and gets looked at in
         * the log.
         */
        seedQuietly("NSFW hidden by default") { NsfwDefaults.seedHiddenByDefault() }
        /*
         * One coroutine for both, and on IO.
         *
         * Both of them read-modify-write the same two preferences — the incognito set, and the
         * record of what has already been seeded. Run side by side they can each read the set
         * before either has written, and the loser's addition is lost; because the *seeded* record
         * is what says "already handled", the lost entry is never retried on a later launch. The
         * result would be an extension or an addon that silently stays out of incognito for good.
         *
         * IO rather than the default Main.immediate, because the first of these evaluates
         * `AnimeExtensionManager.installedExtensionsFlow` before its first suspension — and
         * building that manager loads and DEX-verifies every installed extension. On the main
         * thread, inside onCreate, that is the launch freezing for as long as that takes.
         *
         * The first never completes, so the second is started after it rather than sequenced by it.
         */
        lifecycleScope.launchIO {
            seedQuietly("adult addon incognito") { NsfwDefaults.seedIncognitoForAdultAddons() }
        }
        lifecycleScope.launchIO { seedQuietly("NSFW incognito") { NsfwDefaults.seedIncognitoForNsfw() } }

        /*
         * Schedule the periodic anime library update, and keep it scheduled.
         *
         * Mihon schedules its own from `SetupLibraryUpdateMigration`, a migration that runs on
         * every start — a list we cannot add to, so nothing had ever scheduled the anime one and
         * automatic episode checks simply never happened.
         *
         * The two jobs hold separate slots but read the *same* preferences, which are Mihon's:
         * changing the interval or the device restrictions in settings calls Mihon's `setupTask`
         * and knows nothing of ours. So the changes are observed rather than read once, and the
         * anime job follows the same setting rather than lagging a restart behind it.
         */
        lifecycleScope.launch {
            merge(
                libraryPreferences.autoUpdateInterval.changes().map {},
                libraryPreferences.autoUpdateDeviceRestrictions.changes().map {},
            )
                .onStart { emit(Unit) }
                .collect { AnimeLibraryUpdateJob.setupTask(this@MainActivity) }
        }

        /*
         * Reclaim the disk left behind by entries that have left the library.
         *
         * Once per launch rather than on a schedule: the files are not urgent — they are already
         * doing nothing — and a periodic job that deletes things has to be right about a great deal
         * more, including what is happening while nobody is watching. Launch is a moment when the
         * library is settled and a downloader almost certainly is not running, and the sweep checks
         * for one anyway.
         *
         * On IO because it lists directories, and detached from the result because there is nothing
         * to tell anyone: a sweep that finds nothing is the normal case, and the settings screen is
         * where someone who wants a number can ask for one.
         */
        lifecycleScope.launchIO {
            // Same wall as the seeds above, for the same reason: this is the other piece of
            // launch-time housekeeping, and building the sweeper resolves the source manager —
            // which for many launches is the first touch of the anime database, and therefore
            // where a failed migration would surface. Tidying up is not worth the app.
            seedQuietly("orphaned download sweep") {
                if (downloadCleanupPreferences.deleteWhenRemovedFromLibrary().get()) {
                    val result = OrphanedDownloadSweeper().sweep()
                    if (result.total > 0) {
                        logcat { "Removed ${result.manga} manga and ${result.anime} anime download folders" }
                    }
                }
            }
        }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setContent {
            // Every list item asks this once rather than asking the system service per item,
            // and it is inside the theme so the focus border can read the accent colour.
            AnimatoTheme {
                ProvideIsTelevision {
                    val context = LocalContext.current

                    // Asked once, on the launch after a crash — which is the only moment somebody
                    // has a reason to care that a report exists.
                    CrashReportPrompt()

                    var incognito by remember { mutableStateOf(getIncognitoState.await(null)) }
                    val downloadOnly by preferences.downloadedOnly.collectAsState()
                    val indexing by downloadCache.isInitializing.collectAsState()

                    val isSystemInDarkTheme = isSystemInDarkTheme()
                    val statusBarBackgroundColor = when {
                        indexing -> IndexingBannerBackgroundColor
                        downloadOnly -> DownloadedOnlyBannerBackgroundColor
                        incognito -> IncognitoModeBannerBackgroundColor
                        else -> MaterialTheme.colorScheme.surface
                    }
                    LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor) {
                        // Draw edge-to-edge and set system bars color to transparent
                        val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                        val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                        enableEdgeToEdge(
                            statusBarStyle = if (statusBarBackgroundColor.luminance() > 0.5) lightStyle else darkStyle,
                            navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                        )
                    }

                    Navigator(
                        screen = AnimatoHomeScreen,
                        disposeBehavior = NavigatorDisposeBehavior(
                            disposeNestedNavigators = false,
                            disposeSteps = true,
                        ),
                    ) { navigator ->
                        LaunchedEffect(navigator) {
                            this@MainActivity.navigator = navigator

                            if (isLaunch) {
                                // Set start screen
                                handleIntentAction(intent, navigator)

                                // Reset Incognito Mode on relaunch
                                preferences.incognitoMode.set(false)
                            }

                            // See the note on `ready`: Mihon's tabs cannot reach this activity to set it.
                            ready = true
                        }
                        // Which source, if any, is being browsed right now — the banner is
                        // per-source, because incognito is. `SourceBrowseScreen` is what browsing
                        // a source is in this app; upstream's is still named for the case where a
                        // deep link or a shortcut lands on it.
                        LaunchedEffect(navigator.lastItem) {
                            when (val screen = navigator.lastItem) {
                                is SourceBrowseScreen -> screen.sourceId
                                is BrowseSourceScreen -> screen.sourceId
                                else -> null
                            }
                                .let(getIncognitoState::subscribe)
                                .collectLatest { incognito = it }
                        }

                        val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                        Scaffold(
                            topBar = {
                                AppStateBanners(
                                    downloadedOnlyMode = downloadOnly,
                                    incognitoMode = incognito,
                                    indexing = indexing,
                                    modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                                )
                            },
                            contentWindowInsets = scaffoldInsets,
                        ) { contentPadding ->
                            // Consume insets already used by app state banners
                            Box {
                                // Shows current screen
                                DefaultNavigatorScreenTransition(
                                    navigator = navigator,
                                    modifier = Modifier
                                        .padding(contentPadding)
                                        .consumeWindowInsets(contentPadding),
                                )

                                // Draw navigation bar scrim when needed
                                if (remember { isNavigationBarNeedsScrim() }) {
                                    Spacer(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                            .alpha(0.8f)
                                            .background(MaterialTheme.colorScheme.surfaceContainer),
                                    )
                                }
                            }
                        }

                        // Pop source-related screens when incognito mode is turned off
                        LaunchedEffect(Unit) {
                            preferences.incognitoMode.changes()
                                .drop(1)
                                .filter { !it }
                                .onEach {
                                    val currentScreen = navigator.lastItem
                                    // EntryScreen is ours and replaced Mihon's as the page a source
                                    // result opens, so the rule has to name it too — otherwise
                                    // leaving incognito quietly stopped popping anything.
                                    if (currentScreen is BrowseSourceScreen ||
                                        currentScreen is SourceBrowseScreen ||
                                        (currentScreen is MangaScreen && currentScreen.fromSource) ||
                                        (currentScreen is EntryScreen && currentScreen.fromSource)
                                    ) {
                                        navigator.popUntilRoot()
                                    }
                                }
                                .launchIn(this)
                        }

                        HandleOnNewIntent(context = context, navigator = navigator)

                        if (isLaunch) CheckForUpdates()
                        ShowOnboarding()
                    }
                }
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION)
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch && libraryPreferences.autoClearChapterCache.get()) {
            lifecycleScope.launchIO {
                chapterCache.clear()
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator?.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest { handleIntentAction(it, navigator) }
        }
    }

    /**
     * Offers a newer Animato, once, on a cold start.
     *
     * The check is ours — [AnimatoAppUpdateChecker] sets out why Mihon's cannot be pointed at this
     * repository — but the screen that follows is Mihon's, unchanged: it takes a download link and
     * knows nothing about whose release it came from.
     *
     * Failing quietly is deliberate. There is no network on a plane and no GitHub behind some
     * firewalls, and neither is a reason to interrupt someone opening their library.
     */
    @Composable
    private fun CheckForUpdates() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            if (!AnimatoAppUpdateChecker.isEnabled) return@LaunchedEffect
            try {
                val release = AnimatoAppUpdateChecker().checkForUpdate() ?: return@LaunchedEffect
                navigator.push(
                    NewUpdateScreen(
                        versionName = release.version,
                        changelogInfo = release.info,
                        releaseLink = release.releaseLink,
                        downloadLink = release.downloadLink,
                    ),
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }

        /*
         * And whether the extensions have updates waiting.
         *
         * Mihon runs this from its own `MainActivity` on every cold start. Ours is an adapted copy
         * of that file and the block was not in it, because the class it calls — `ExtensionApi` —
         * is `internal` to Mihon's module and cannot be named from ours. So it was dropped during
         * the port, silently, and neither half has checked since. [ExtensionUpdateCheck] rebuilds
         * it on the public pieces and covers both.
         *
         * Separate from the app update above rather than folded into it: that one pushes a screen
         * and this one posts a notification, and an extension repository being down should not stop
         * someone being told about a new build of the app.
         */
        LaunchedEffect(Unit) {
            ExtensionUpdateCheck().check(this@MainActivity)
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            // Ours, not Mihon's. Theirs is four steps about the device and knows nothing about the
            // lens, which is the one idea somebody has to meet before the rest of the app makes
            // sense. See AnimatoOnboardingScreen for what was kept from theirs and where it went.
            if (!preferences.shownOnboardingFlow.get() && navigator.lastItem !is AnimatoOnboardingScreen) {
                navigator.push(AnimatoOnboardingScreen())
            }
        }
    }

    /**
     * How the splash hands over to the app.
     *
     * The brush wordmark leaves the way it was drawn: drifting a little to the right, along the
     * direction of the stroke, swelling very slightly as it goes. It reads as the last moment of a
     * stroke being finished rather than a logo being dismissed. The app rises the final few dp
     * underneath it at the same time, so the two are one movement instead of a disappearance
     * followed by an appearance.
     *
     * Taken over on every version now, not only before Android 12. The platform's own exit is a
     * centred zoom-and-fade designed for a circular launcher icon, and a wordmark two and a half
     * times wider than it is tall pulses oddly inside it. Claiming the listener replaces that,
     * which is the point — and it leaves one animation to look at rather than two behaviours split
     * by OS version.
     *
     * The bars go transparent so nothing draws a hard edge across the fade, and are left that way:
     * the activity sets its own system-bar appearance immediately afterwards.
     */
    @Suppress("Deprecation")
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        if (splashScreen == null) return
        val root = findViewById<View>(android.R.id.content)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        splashScreen.setOnExitAnimationListener { splashProvider ->
            // The compat layer applies a Y translation of its own to the icon, which fights
            // anything set here.
            splashProvider.iconView.translationY = 0F

            val markAnim = ValueAnimator.ofFloat(0F, 1F).apply {
                interpolator = FastOutSlowInInterpolator()
                duration = SPLASH_EXIT_ANIM_DURATION
                addUpdateListener { va ->
                    val t = va.animatedValue as Float
                    splashProvider.iconView.apply {
                        // Outward along the stroke rather than upward: the letters lean right, so
                        // that is the direction the eye already expects them to leave in.
                        translationX = t * SPLASH_MARK_DRIFT_DP.dpToPx
                        scaleX = 1F + t * SPLASH_MARK_SWELL
                        scaleY = 1F + t * SPLASH_MARK_SWELL
                        alpha = 1F - t
                    }
                    splashProvider.view.alpha = 1F - t
                }
                doOnEnd { splashProvider.remove() }
            }

            val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                interpolator = LinearOutSlowInInterpolator()
                duration = SPLASH_EXIT_ANIM_DURATION
                addUpdateListener { va ->
                    root.translationY = (va.animatedValue as Float) * 16.dpToPx
                }
            }

            activityAnim.start()
            markAnim.start()
        }
    }

    /**
     * Maps an incoming intent onto a tab or a screen.
     *
     * This is what makes the `activity-alias` in our manifest worth having. Mihon builds eight
     * `PendingIntent`s naming `eu.kanade.tachiyomi.ui.main.MainActivity` — notification taps, the
     * reader's "back to library", the OAuth callback, crash recovery — and the alias routes that
     * component name here. The actions and extras below are Mihon's own constants, read out of the
     * same intents, so all eight keep working against our activity without a line of Mihon's code
     * being touched.
     */
    private fun handleIntentAction(intent: Intent, navigator: Navigator): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            // Mihon's own NotificationReceiver.dismissNotification is `internal`, so it is not
            // reachable from here. AnimeNotifications.dismiss is ours and already implements the
            // same rule — cancelling a grouped notification has to cancel the group summary too,
            // because dismissing programmatically does not do it the way a user swipe does.
            AnimeNotifications.dismiss(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_LIBRARY -> AnimatoTab.LIBRARY
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                navigator.push(MangaScreen(idToOpen))
                setContentLens(ContentFilter.MANGA)
                AnimatoTab.LIBRARY
            }
            AnimeConstants.SHORTCUT_ANIMELIB -> {
                setContentLens(ContentFilter.ANIME)
                AnimatoTab.LIBRARY
            }
            // The anime notifications carry the id under Mihon's own extra key, which is what
            // Aniyomi did too — one key for "the entry this intent is about", whichever it is.
            AnimeConstants.SHORTCUT_ANIME -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                navigator.push(AnimeScreen(idToOpen))
                setContentLens(ContentFilter.ANIME)
                AnimatoTab.LIBRARY
            }
            Constants.SHORTCUT_UPDATES -> AnimatoTab.UPDATES
            // History has no destination of its own; it is the continue rail on Home.
            Constants.SHORTCUT_HISTORY -> AnimatoTab.HOME
            Constants.SHORTCUT_SOURCES -> AnimatoTab.DISCOVER
            Constants.SHORTCUT_EXTENSIONS -> {
                BrowseTab.showExtension()
                setContentLens(ContentFilter.MANGA)
                AnimatoTab.DISCOVER
            }
            AnimeConstants.SHORTCUT_ANIMEEXTENSIONS -> {
                setContentLens(ContentFilter.ANIME)
                AnimatoTab.DISCOVER
            }
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popUntilRoot()
                setContentLens(ContentFilter.MANGA)
                AnimatoTab.UPDATES
            }
            AnimeConstants.SHORTCUT_ANIME_DOWNLOADS -> {
                navigator.popUntilRoot()
                setContentLens(ContentFilter.ANIME)
                AnimatoTab.UPDATES
            }
            Intent.ACTION_APPLICATION_PREFERENCES -> {
                navigator.popUntilRoot()
                navigator.push(SettingsScreen())
                null
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // Matches the standard Android search intent, or the Google-specific one raised by
                // saying or typing "search <query> on Animato" to Google Search or the Assistant.
                val query = intent.getStringExtra(SearchManager.QUERY)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()
                    // Two activities answer these filters and both hand the intent here, where they
                    // are indistinguishable — so the anime one stamps the intent on the way past.
                    // Mihon's cannot stamp anything, being Mihon's, which is what makes "no stamp"
                    // mean manga rather than an unanswered question.
                    navigator.push(
                        if (intent.getStringExtra(DeepLinkScreenType.INTENT_SEARCH_TYPE) ==
                            DeepLinkScreenType.ANIME.toString()
                        ) {
                            DeepLinkAnimeScreen(query)
                        } else {
                            DeepLinkScreen(query)
                        },
                    )
                }
                null
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalSearchScreen(query, filter))
                }
                null
            }
            INTENT_ANIMESEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query, filter))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    navigator.popUntilRoot()
                    // Ours, not Mihon's: a .tachibk opened from a file manager is as likely to be
                    // an Aniyomi backup as a Mihon one, and Mihon's reader rejects those outright.
                    navigator.push(AniyomiImportScreen(intent.data.toString(), isAniyomiImport = false))
                }
                // Deep link to add extension store, to whichever half the scheme names
                else if (intent.isAddExtensionStoreIntent()) {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.popUntilRoot()
                        navigator.push(
                            if (intent.isAddAnimeExtensionStoreIntent()) {
                                AnimeExtensionStoresScreen(repoUrl)
                            } else {
                                ExtensionStoresScreen(repoUrl)
                            },
                        )
                    }
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            AnimatoNavigator.openTab(tabToOpen)
        }

        ready = true
        return true
    }

    /**
     * Run a first-launch default, and let it fail without taking the app with it.
     *
     * Cancellation is rethrown, since a cancelled scope is the activity going away rather than the
     * work going wrong, and swallowing it would leave a coroutine that outlives its own screen.
     */
    private inline fun seedQuietly(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Seeding $what failed; carrying on without it" }
        }
    }

    private fun Intent.isAddExtensionStoreIntent(): Boolean {
        return (scheme == "tachiyomi" && data?.host == "add-repo") ||
            (scheme == "mihon" && data?.host == "extension-store") ||
            isAddAnimeExtensionStoreIntent()
    }

    /**
     * Whether this link is an *anime* store link, which is a question the URL cannot answer.
     *
     * An index address looks the same for both halves, so the only thing that says which one a
     * link means is the scheme it was published under — and the ecosystems are separate: an anime
     * repository is shared as `aniyomi://add-repo`, a manga one as `tachiyomi://` or `mihon://`.
     *
     * Until now only the second pair was handled and every link, whichever it was, opened the
     * manga stores screen. Following an anime repository link therefore added it as a manga store,
     * where it lists nothing at all — the failure looks like a dead repository rather than like a
     * link that went to the wrong place.
     */
    private fun Intent.isAddAnimeExtensionStoreIntent(): Boolean =
        scheme == "aniyomi" && data?.host == "add-repo"

    companion object {
        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"

        /** Aniyomi's action, unchanged, so anything that already sends it keeps working. */
        const val INTENT_ANIMESEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}

// Splash screen
private const val SPLASH_MIN_DURATION = 500 // ms
private const val SPLASH_MAX_DURATION = 5000 // ms
private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms

// How the wordmark leaves. Both are deliberately small: this is the punctuation at the end of a
// launch, and anything a person has time to watch on every single launch is too much.
private const val SPLASH_MARK_DRIFT_DP = 20
private const val SPLASH_MARK_SWELL = 0.06F
