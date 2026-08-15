plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "animato.ui"
}

dependencies {
    // Aniyomi generalised these components by editing Mihon's own files. Here they are copies in a
    // module of ours, so Mihon keeps MangaCover and we keep ItemCover, and neither blocks the other.
    // Mihon's app is a library here, so its shared composables and resources are simply
    // available. Consuming them is the point; editing them is what we never do.
    implementation(projects.app)
    implementation(projects.presentationCore)
    implementation(projects.domain)
    implementation(projects.anime.domain)
    implementation(projects.anime.sourceApi)
    implementation(projects.i18n)
    implementation(projects.i18nAnime)
    implementation(libs.injekt)
    implementation(projects.core.common)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIcons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.compose.uiUtil)

    implementation(libs.bundles.coil)
    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(animato.kotlinx.immutables)
}
