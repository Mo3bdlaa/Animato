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

        buildConfigField("String", "ANIMATO_RELEASE_REPO", "\"Mo3bdlaa/Animato\"")

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
     * every single release — the collect step publishes the four splits and nothing else. That is
     * nearly half the packaging work in a release doing nothing at all. The four splits cover every
     * Android device there is, and the one case a universal APK answers — not knowing the
     * architecture up front — does not arise when the download page can ask.
     */
    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
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

    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
