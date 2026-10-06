plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "animato.anime.services"
}

kotlin {
    compilerOptions {
        // The same opt-ins Mihon's app declares. The code moved here came from a module that
        // had them, so without these the ported files fail on APIs they always used.
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

/*
 * A constructor parameter with a default value is a dependency like any other. Metro's default is to
 * treat it as optional — inject it if the graph has it, use the default if not — and in code that
 * came from Injekt the default is almost always `Injekt.get()`, so a binding the graph lacks would
 * compile and then be looked up at run time anyway. Requiring `@OptionalBinding` for that makes a
 * missing binding a build error, which is the whole point of having moved off Injekt.
 */
metro {
    optionalBindingBehavior.set(dev.zacsweers.metro.gradle.OptionalBindingBehavior.REQUIRE_OPTIONAL_BINDING)
}

dependencies {
    implementation(libs.metro.runtime)
    // Mihon's app is a library here: its notification, storage and network helpers are consumed
    // as they are.
    implementation(projects.app)

    implementation(projects.anime.data)
    implementation(projects.anime.domain)
    implementation(projects.anime.sourceApi)
    implementation(projects.anime.sourceLocal)
    implementation(projects.i18nAnime)

    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.sourceApi)
    implementation(projects.core.common)
    implementation(projects.core.archive)
    implementation(projects.i18n)

    // Depending on Mihon's app pulls its Compose artifacts in transitively, and those take
    // their versions from the BOM rather than carrying their own.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.serialization)
    implementation(libs.injekt)
    implementation(libs.unifile)
    implementation(libs.rxJava)
    implementation(libs.jsoup)
    implementation(libs.okhttp.core)
    implementation(libs.logcat)
    implementation(libs.androidx.work)

    implementation(libs.bundles.coil)
    implementation(libs.bundles.shizuku)

    implementation(animato.kotlinx.immutables)
    implementation(animato.ffmpeg.kit)
    implementation(animato.arthenica.smartexceptions)
    implementation(animato.torrserver)
    implementation(animato.localbroadcastmanager)

    // The backup format is the one thing here that has to be exactly right and can be checked
    // without a device: a field number read wrong loses somebody's library.
    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
