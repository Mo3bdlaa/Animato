plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
    // The other modules we own are format-checked; this one held only DI wiring and was missed.
    // It has source worth checking now, and `spotlessCheck` at the root gates every release.
    alias(mihonx.plugins.spotless)

    /*
     * The updater's DTOs are @Serializable and this module had no serialization plugin, so nothing
     * generated a serializer for them. That compiles perfectly — the annotation is just an
     * annotation — and throws the first time the response is decoded:
     *
     *   SerializationException: Serializer for class 'GithubReleaseSummary' is not found.
     *
     * Inside the update check's own catch, which is to say silently. Caught by a test that decodes
     * a real GitHub response rather than a hand-built object.
     */
    alias(libs.plugins.kotlin.serialization)
}

/**
 * The television build, asked for with `-Panimato-tv`.
 *
 * At file scope because two blocks below need it: `defaultConfig`, for the flags the app reads
 * at runtime, and `packaging`, for the libraries it leaves out.
 */
val isTvBuild = project.hasProperty("animato-tv")

android {
    namespace = "io.github.mo3bdlaa.animato"

    defaultConfig {
        // Shared with :app, which compiles it into BuildConfig.APPLICATION_ID — the comment there
        // sets out why that constant cannot stay Mihon's.
        applicationId = providers.gradleProperty("animato.applicationId").get()

        /*
         * Alpha builds set this from the workflow's run number, so each one outranks the last and
         * Android accepts it as an upgrade. Local builds get 1, which is fine: installing over an
         * equal versionCode is allowed, only a lower one is refused.
         */
        versionCode = System.getenv("ANIMATO_VERSION_CODE")?.toIntOrNull() ?: 1

        /*
         * The updater compares this with the tag of the newest release, so an alpha has to say
         * which alpha it is — otherwise every build calls itself 0.1.0 and alpha 6 looks no newer
         * than alpha 5. The workflow passes `0.1.0-alpha.<run number>`.
         *
         * A local build keeps the plain version, which by semver outranks every prerelease of it,
         * so a development build is never offered an alpha.
         */
        versionName = System.getenv("ANIMATO_VERSION_NAME")?.takeIf(String::isNotBlank) ?: "0.1.0"

        /*
         * Where the updater looks for releases, and what the release links point at.
         *
         * Read from the environment rather than written down here, because the environment already
         * knows: GitHub Actions sets GITHUB_REPOSITORY to `owner/name` for every run, so a build
         * always names the repository it was actually built from.
         *
         * Writing it down was a quiet trap. Renaming the project, or moving it to an organisation,
         * would leave every shipped APK asking a repository that no longer answers — and that
         * failure does not surface at the transfer. It surfaces months later as a 404 on somebody's
         * phone, which is exactly how this file's previous value announced itself.
         *
         * A local build has no such variable and falls back to the property, which only affects
         * development builds: those are never distributed, so the value barely matters.
         */
        buildConfigField(
            "String",
            "ANIMATO_RELEASE_REPO",
            "\"${
                providers.environmentVariable("GITHUB_REPOSITORY")
                    .orElse(providers.gradleProperty("animato.releaseRepo"))
                    .get()
            }\"",
        )

        /*
         * On, unless a build says otherwise with `-Panimato-no-updater`.
         *
         * Ours rather than Mihon's `UPDATER_ENABLED`, which the update check used to read — and
         * which is `project.hasProperty("enable-updater")`, so it is **false** unless a build passes
         * that flag. Mihon's release pipeline passes it; ours never did, so `CheckForUpdates`
         * returned at its first line and the updater shipped in alpha.6 and alpha.7 without ever
         * having run. It gated a feature off by default and nothing said so.
         *
         * The flag itself is worth keeping — F-Droid forbids an app that updates itself, which is
         * exactly why Mihon has one — but the default belongs the other way round: an updater that
         * has to be switched on is an updater that is off.
         */
        buildConfigField(
            "boolean",
            "UPDATER_ENABLED",
            "${!project.hasProperty("animato-no-updater")}",
        )

        /*
         * The television build, asked for with `-Panimato-tv`.
         *
         * It exists for a reason with a number on it. The APK is 95 MB on armeabi-v7a and 88 MB of
         * that is native code — all of it genuinely used, so there was no waste to delete and no
         * stripping left to do (the libraries ship stripped; measuring it saves 0.5 MB). The only
         * honest way to make it smaller was to take something out, and on a television the obvious
         * something is the manga reader: two image decoders and a WebGPU viewer, 28.6 MB between
         * them, for reading comics with a remote control from three metres away.
         *
         * ## Why a property and not a product flavour
         *
         * A flavour is the textbook answer and it was the wrong one here. Flavours rename every
         * variant task — `compileReleaseKotlin` becomes `compileMobileReleaseKotlin`,
         * `testDebugUnitTest` becomes `testMobileDebugUnitTest` — and this repository's CI runs five
         * bespoke scripts keyed to those names and to the output paths under them. Worse than the
         * breakage is the shape of it: `./gradlew testDebugUnitTest` would keep *succeeding*,
         * because other modules still have that task, while this module's tests quietly stopped
         * running. A property changes no task name, so every lane and every script keeps working
         * and the release workflow simply runs `assembleRelease` a second time with it set.
         *
         * ## Why excluding the libraries is only half of it
         *
         * The Kotlin is identical in both builds, so the reader's code is still here and would load
         * a library that is not. That is why this also fixes the lens to anime — see
         * `ContentPreferences.fixedTo`. Not a cosmetic hide: the lens is what the library, the
         * rails, search and *the extensions list* all filter by, so a manga source cannot be
         * installed on this build, let alone opened.
         */
        buildConfigField("boolean", "ANIMATO_ANIME_ONLY", "$isTvBuild")

        /*
         * Which release asset this build updates itself from.
         *
         * The variant goes *after* the architecture — `…-arm64-v8a-tv.apk`, not `…-tv-arm64-v8a.apk`
         * — and that ordering is load-bearing. The updater matches on the end of the asset name, so
         * a variant in the middle would leave the television file ending in `-arm64-v8a.apk` like
         * the phone's, and a phone would have been offered a build with no manga libraries in it.
         * It would have found out when somebody opened a chapter. Putting it last also means every
         * build already installed keeps updating correctly without knowing this flag exists.
         */
        buildConfigField(
            "String",
            "ANIMATO_UPDATE_ASSET_SUFFIX",
            if (isTvBuild) "\"-tv.apk\"" else "\".apk\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }


    /*
     * One APK per architecture, as Aniyomi and Mihon both ship.
     *
     * This matters far more here than in a manga reader: mpv, FFmpeg and the torrent server are
     * native, and carrying all four architectures in one APK costs 342 MB of the 380 MB it came to
     * without this. Splitting brings each one to roughly a quarter of that.
     *
     * **No universal APK.** It was 361 MB, and it was built, zipaligned, signed and then deleted on
     * every single release — the collect step publishes the splits and nothing else. That is nearly
     * half the packaging work in a release doing nothing at all. The one case a universal APK
     * answers — not knowing the architecture up front — does not arise when the download page can
     * ask.
     *
     * **No x86 either.** Those two builds exist for emulators, cost 222 MB to produce and sign, and
     * nobody was going to install one: an emulator is a way to test a phone app, and the phone is
     * right there. Every physical Android device is ARM. If a desktop build ever happens it will not
     * be an x86 APK — see ROADMAP.md.
     */
    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            // Stripping these breaks mpv's own crash reporting, and they are what Aniyomi kept.
            keepDebugSymbols += listOf(
                "libavcodec",
                "libavdevice",
                "libavfilter",
                "libavformat",
                "libavutil",
                "libc++_shared",
                "libffmpegkit_abidetect",
                "libffmpegkit",
                "libmpv",
                "libplayer",
                "libpostproc",
                "libswresample",
                "libswscale",
                "libtorrserver",
            ).map { "**/$it.so" }

            /*
             * The manga reader's native half, left out of the television build.
             *
             * 28.6 MB of the APK, and all three are reached only by opening a chapter: two image
             * decoders — Mihon ships both, deliberately — and the WebGPU viewer behind one of its
             * reading modes. The lens being fixed to anime is what makes sure nothing ever asks for
             * them; this line is only the saving. Taking one without the other is a crash, so they
             * are both read off the same flag.
             *
             * Measured rather than guessed: every other native library in here is either the player
             * (mpv and FFmpeg, 23 MB) or something the whole app needs, and the libraries already
             * ship stripped, so there was nothing else to take.
             */
            if (isTvBuild) {
                excludes += listOf(
                    "libimagedecoder",
                    "libimagedecoder2",
                    "libwebgpu_c_bundled",
                ).map { "**/$it.so" }
            }
        }
    }

    /*
     * R8 runs here and nowhere else.
     *
     * It used to run on `:app`, which is a library in this build, and that was worse than not
     * running at all: R8 optimises on the assumption that it can see every caller, so it narrowed
     * a parameter type Mihon's own callers happened to satisfy and our MainActivity did not, and
     * the app died with NoSuchMethodError before drawing a frame. On the application module R8 sees
     * the whole program, which is the only place that assumption holds.
     *
     * Both `source-api` modules send their extension-API rules as consumer rules, which needs no
     * help. Mihon's own rules cannot travel that way — `proguard-rules.pro` opens with
     * `-dontobfuscate`, and AGP rejects a global option in a consumer file, since it would change
     * the terms for every consumer without saying so. So the file is named directly here, where a
     * global option is legal. If Mihon moves or renames it the build fails, which is the right
     * failure: these rules going missing is not something to discover at runtime.
     *
     * `proguard-rules.pro` in this module adds only what neither can know about — this fork's own
     * packages and the native libraries the anime side brought.
     */
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("app/proguard-rules.pro"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        // MainActivity is an adapted copy of Mihon's, which its own module compiles with these.
        // Notably Scaffold in :presentation-core is @ExperimentalMaterial3Api.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    // Mihon, consumed as a library. Nothing in this module edits it.
    implementation(projects.app)

    // Our theme and generalised components. MainActivity applies AnimatoTheme from here.
    implementation(projects.animatoUiKit)

    implementation(projects.anime.data)
    implementation(projects.anime.domain)
    implementation(projects.anime.player)
    implementation(projects.anime.services)
    implementation(projects.anime.sourceApi)
    implementation(projects.anime.sourceLocal)
    // The anime screens. Nothing navigates to them yet — phase 6c builds the tab bar that does —
    // but the dependency is what makes CI compile them, since it only builds this module.
    implementation(projects.anime.ui)

    // The Injekt modules construct the anime database and repositories, so they need what those
    // constructors take: Mihon's shared core, its column adapters, and the SQLDelight driver.
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.core.common)
    // The download folders the orphan sweep walks are UniFiles, which is how both halves' download
    // providers hand a directory back.
    implementation(libs.unifile)

    implementation(libs.injekt)
    implementation(libs.bundles.sqldelight)
    // The SQLite the anime database runs on, carried in the APK rather than taken from the device.
    // AnimeAppModule says why that is a fix and not a preference.
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.bundles.serialization)
    implementation(libs.bundles.kotlinx.coroutines)

    // Mihon's app declares Compose artifacts without versions; they arrive transitively from it.
    implementation(platform(libs.androidx.compose.bom))

    /*
     * MainActivity's own needs. Mihon declares all of these too, but `implementation` does not leak
     * to consumers, so depending on Mihon's app does not bring its Compose or Voyager with it.
     * Declaring them here is what makes them ours to compile against, and the BOM above keeps the
     * Compose versions identical to Mihon's rather than merely compatible.
     */
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.coreSplashScreen)
    implementation(libs.bundles.voyager)
    implementation(projects.presentationCore)
    implementation(projects.i18n)

    // The tab bar and the home screen: animated tab icons, the fade between destinations, the
    // Material icon set the two new destinations use, and view models for the home screen.
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationGraphics)
    implementation(libs.androidx.compose.materialIcons)
    implementation(libs.composeMaterialMotion)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(projects.i18nAnime)
    implementation(libs.bundles.coil)
    implementation(animato.kotlinx.immutables)
    // Discover's metadata rails POST a GraphQL body to AniList, which needs a RequestBody rather
    // than the query string a GET takes — so okhttp is named here rather than arriving through
    // Mihon's network helper.
    implementation(libs.bundles.okhttp)
    // The swipe box Mihon's own chapter rows use. Reused rather than reimplemented so a swipe on
    // the updates feed feels identical to a swipe on a title page.
    implementation(libs.swipe)
    // The library-sync worker. Both halves' backup jobs are WorkManager already; this schedules
    // beside them rather than inventing a second way to run something periodically.
    implementation(libs.androidx.work)

    /*
     * Nothing in this module touches LocalBroadcastManager. Mihon's `ExtensionInstaller` does, and
     * that is enough — installing an extension died with NoClassDefFoundError on a device while
     * every build here was green.
     *
     * Mihon never declares it either. It reaches it through `dynamicanimation:1.0.0`, which brings
     * `legacy-support-core-utils`, which brings this. Resolved on its own, `:app` gets 1.0.0 and
     * compiles. Resolved as part of this application, something else raises `dynamicanimation` to
     * 1.1.0 — a version that dropped the legacy dependency — and the class simply is not there.
     *
     * So the failure is invisible to every check we have: the code compiles against a graph that
     * differs from the one it ships in, and only the running app resolves the version that loses.
     * Declaring it here states the requirement in the module that has to satisfy it.
     */
    implementation(animato.localbroadcastmanager)

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
