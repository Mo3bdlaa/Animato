plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
    // The other modules we own are format-checked; this one held only DI wiring and was missed.
    // It has source worth checking now, and `spotlessCheck` at the root gates every release.
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "io.github.mo3bdlaa.animato"

    defaultConfig {
        applicationId = "io.github.mo3bdlaa.animato"

        /*
         * Alpha builds set this from the workflow's run number, so each one outranks the last and
         * Android accepts it as an upgrade. Local builds get 1, which is fine: installing over an
         * equal versionCode is allowed, only a lower one is refused.
         */
        versionCode = System.getenv("ANIMATO_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
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
     * The universal APK is kept for the cases where the architecture is not known up front.
     */
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
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
    // Mihon's bundle carries only the androidx driver, which needs an async schema.
    // The version is read from Mihon's catalogue so this cannot drift away from it.
    implementation(
        libs.sqldelight.coroutines.map { "app.cash.sqldelight:android-driver:${it.version}" },
    )
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
