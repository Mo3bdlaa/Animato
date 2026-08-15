plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "animato.anime.player"

    buildFeatures {
        // player_layout.xml is a view hierarchy, not Compose: the mpv surface is a plain View.
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        // The same opt-ins Mihon's app declares; the ported files came from a module that had them.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
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
    implementation(projects.presentationCore)
    implementation(projects.animatoUiKit)
    // The custom-button settings list is drag-to-reorder.
    implementation(libs.reorderable)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(projects.core.common)
    implementation(projects.i18n)

    // Depending on Mihon's app pulls its Compose artifacts in transitively, and those take
    // their versions from the BOM rather than carrying their own.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.bundles.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.serialization)
    implementation(libs.injekt)
    implementation(libs.unifile)
    implementation(libs.logcat)
    implementation(libs.okhttp.core)
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

    // Playback and the player's own UI pieces. None of this exists in Mihon.
    implementation(animato.aniyomi.mpv)
    implementation(animato.ffmpeg.kit)
    implementation(animato.arthenica.smartexceptions)
    implementation(animato.mediasession)
    implementation(animato.seeker)
    implementation(animato.truetypeparser)
    implementation(animato.compose.constraintlayout)
    implementation(animato.constraintlayout)
    implementation(animato.kotlinx.immutables)
}
