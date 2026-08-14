plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "aniyomi.domain"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    // Anime domain types expose shared domain types (categories, tracks, display modes) in their
    // public signatures, so consumers need them too.
    api(projects.domain)

    implementation(projects.anime.sourceApi)
    implementation(projects.core.common)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation(kotlinx.bundles.serialization)

    api(libs.sqldelight.android.paging)

    compileOnly(libs.compose.stablemarker)

    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
