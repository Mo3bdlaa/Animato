plugins {
    id("com.android.application")
    kotlin("android")
}

// These four are what the index generator and the APK name are built from.
ext {
    set("extName", "Example")
    set("pkgNameSuffix", "ar.example")
    set("extClass", ".ExampleSource")
    set("extVersionCode", 1)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension.ar.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.animeextension.${property("pkgNameSuffix")}"
        minSdk = 21
        targetSdk = 35
        versionCode = property("extVersionCode") as Int
        // The version people see. Bump the code above on every release, or the app will not
        // offer the update — it compares codes, not names.
        versionName = "1.0.$versionCode"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Available at runtime from the app, so also compileOnly.
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.18.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
