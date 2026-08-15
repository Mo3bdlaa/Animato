plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "animato.anime.player"
}

dependencies {
    // Mihon's app is a library here: its network and storage helpers are consumed as they are.
    implementation(projects.app)

    implementation(projects.anime.services)
    implementation(projects.anime.domain)
    implementation(projects.anime.sourceApi)
    implementation(projects.anime.sourceLocal)
    implementation(projects.i18nAnime)

    implementation(projects.domain)
    implementation(projects.core.common)
    implementation(projects.i18n)

    // Depending on Mihon's app pulls its Compose artifacts in transitively, and those take
    // their versions from the BOM rather than carrying their own.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.injekt)
    implementation(libs.unifile)
    implementation(libs.logcat)
}
