plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)
}

android {
    namespace = "animato.anime.ui"
}

kotlin {
    compilerOptions {
        // The same opt-ins Mihon's app declares; these screens came from a module that had them.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    // Mihon's app is a library here. Its shared composables, dialogs and utilities are consumed as
    // they are; nothing in this module edits them.
    implementation(projects.app)

    implementation(projects.anime.domain)
    implementation(projects.anime.services)
    implementation(projects.anime.sourceApi)
    implementation(projects.anime.sourceLocal)
    implementation(projects.anime.player)
    implementation(projects.i18nAnime)

    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.presentationCore)
    implementation(projects.animatoUiKit)
    implementation(projects.core.common)
    implementation(projects.core.viewmodel)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.i18n)

    // Compose artifacts arrive transitively from Mihon's app without versions; the BOM supplies them.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.serialization)
    implementation(libs.injekt)
    implementation(libs.logcat)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.voyager)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIcons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationGraphics)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.compose.uiUtil)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)

    implementation(animato.kotlinx.immutables)
    implementation(libs.composeGrid)
}
