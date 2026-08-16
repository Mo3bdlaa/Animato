plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    // `AnimeDetails` and `EpisodeDetails` are @Serializable and `LocalAnimeSource` decodes both with
    // `decodeFromStream`. Without this the annotation generates nothing and the decode throws, so a
    // local anime's details.json and episodes.json were read as failures rather than as metadata.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "animato.source.local"
}

dependencies {
    // Mihon's app is a library here; its file and storage helpers are consumed as they are.
    implementation(projects.app)

    implementation(projects.anime.sourceApi)
    implementation(projects.anime.domain)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.i18nAnime)
    implementation(projects.i18n)

    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.domain)

    // Depending on Mihon's app pulls its Compose artifacts in, and they take versions from the BOM.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.unifile)
    implementation(libs.bundles.serialization)
    implementation(libs.injekt)
    implementation(libs.jsoup)

    implementation(animato.ffmpeg.kit)
    implementation(animato.arthenica.smartexceptions)
}
