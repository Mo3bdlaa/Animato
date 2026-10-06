plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "aniyomi.domain"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
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
    // Anime domain types expose shared domain types - categories, tracks, display modes - in their
    // own public signatures, so consumers need them too.
    api(projects.domain)

    implementation(projects.anime.sourceApi)
    implementation(projects.sourceApi)
    implementation(projects.core.common)

    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.bundles.serialization)

    api(libs.sqldelight.androidxPaging)

    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtimeAnnotation)

    testImplementation(libs.bundles.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
