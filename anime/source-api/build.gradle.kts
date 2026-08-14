plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animesource"

    defaultConfig {
        consumerProguardFiles("consumer-proguard.pro")
    }
}

dependencies {
    // The manga source API, for the handful of helpers both contracts share.
    api(projects.sourceApi)

    implementation(projects.core.common)

    api(libs.kotlinx.serialization.json)
    api(libs.injekt)
    api(libs.rxJava)
    api(libs.jsoup)
    api(libs.androidx.preference)
    api(animato.nanohttpd)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
