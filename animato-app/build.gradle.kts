plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
}

android {
    namespace = "io.github.mo3bdlaa.animato"

    defaultConfig {
        applicationId = "io.github.mo3bdlaa.animato"
        versionCode = 1
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

dependencies {
    // Mihon, consumed as a library. Nothing in this module edits it.
    implementation(projects.app)

    implementation(projects.anime.data)
    implementation(projects.anime.domain)
    implementation(projects.anime.player)
    implementation(projects.anime.services)
    implementation(projects.anime.sourceApi)
    implementation(projects.anime.sourceLocal)

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
}
