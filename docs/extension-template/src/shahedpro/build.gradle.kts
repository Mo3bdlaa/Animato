plugins {
    id("com.android.application")
    kotlin("android")
}

// These four are what the index generator and the APK name are built from.
ext {
    set("extName", "ShahedPro")
    set("pkgNameSuffix", "ar.shahedpro")
    set("extClass", ".ShahedProSource")
    set("extVersionCode", 1)
}

// The extension API this source is written against, and — read the note on versionName — the only
// place Animato actually learns it from. Change it only when moving to a different API.
val extLibVersion = "14.0"

android {
    namespace = "eu.kanade.tachiyomi.animeextension.ar.shahedpro"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.${property("pkgNameSuffix")}"
        minSdk = 21
        targetSdk = 35
        versionCode = property("extVersionCode") as Int

        /*
         * Two separate things are encoded here, and getting it wrong is silent.
         *
         * Animato works out which API version an extension was built against by taking everything
         * before the last dot of the version name: AnimeExtensionLoader requires that to be 14.0
         * or 16.0, and drops the extension with nothing shown anywhere if it is not. So "14.0.1"
         * reads as lib 14, release 1 — and a plain "1.0.1" reads as lib 1.0, which is not a
         * supported API, and produces an extension that installs, appears in Android's app list,
         * and is never seen by Animato.
         *
         * The `tachiyomi.animeextension.lib` manifest entry does not do this job; nothing reads it.
         *
         * The trailing number is also what people see. Bump extVersionCode on every release: the
         * app compares version codes, not names, and a release that forgets is a release nobody is
         * offered.
         */
        versionName = "$extLibVersion.${property("extVersionCode")}"

        /*
         * The name Animato and the store listing show, as a string resource rather than a literal.
         *
         * It has to be a resource: `aapt dump badging` only prints an `application-label:` line for
         * a label it can resolve, and reports `label=''` for an inline string — so the index
         * generator, which reads the APK back rather than the build files, would fall back to
         * naming the extension after its package. Without any label at all the loader does the
         * same, from the other direction.
         */
        resValue("string", "app_name", property("extName") as String)
    }

    /*
     * An unsigned extension is refused before it is ever asked for a source — AnimeExtensionLoader
     * checks for a signature and returns an error ("Package isn't signed") — and `assembleRelease`
     * without a signing config quietly produces `*-release-unsigned.apk` instead of failing. So
     * this block is what stands between a green build and an extension that cannot be loaded.
     *
     * CI writes the keystore out of a repository secret; locally it is the one the README tells you
     * to generate. If neither is there the release stays unsigned rather than failing the build,
     * and CI checks for that separately with apksigner.
     */
    signingConfigs {
        create("release") {
            val keystore = file(System.getenv("KEYSTORE_PATH") ?: rootProject.file("signing.jks").path)
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.setSrcDirs(listOf("src"))
            res.setSrcDirs(listOf("res"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    /*
     * Stubs, not an implementation — `compileOnly` on purpose.
     *
     * The real classes live inside Animato. The extension is compiled against the shape of them
     * and then loaded by the app, which supplies the actual code at runtime. Bundling them would
     * put a second copy of the API in the APK and the class loader would use the wrong one.
     *
     * Version 14 because that is what is published and what Animato's loader accepts
     * (AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS). 16 is not on JitPack.
     */
    compileOnly("com.github.aniyomiorg:extensions-lib:14")

    /*
     * Available at runtime from the app, so also compileOnly — and pinned to the versions Animato
     * actually ships (gradle/libs.versions.toml), because these are compiled against but never
     * bundled. Drifting below them is not harmless: OkHttp 5 made `Response.body` non-null, so
     * `response.body.string()` — what every source in this template is written with — does not
     * compile against 4.x at all.
     */
    compileOnly("com.squareup.okhttp3:okhttp:5.4.0")
    compileOnly("org.jsoup:jsoup:1.23.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
