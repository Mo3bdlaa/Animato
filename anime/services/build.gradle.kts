plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "animato.anime.services"
}

dependencies {
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

    implementation(animato.ffmpeg.kit)
    implementation(animato.arthenica.smartexceptions)
    implementation(animato.torrserver)
}
