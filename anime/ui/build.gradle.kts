plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.compose)
    alias(mihonx.plugins.spotless)
    alias(libs.plugins.metro)
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
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.i18n)
    implementation(projects.icons.simpleIcons)

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
    implementation(animato.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animationGraphics)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.compose.uiUtil)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)

    implementation(animato.kotlinx.immutables)
    implementation(libs.composeGrid)
    implementation(libs.reorderable)
    implementation(libs.swipe)

    // The anime download queue is Aniyomi's RecyclerView screen, not Compose. 6c replaces it with
    // the Downloads destination from the brand sheet; until then it needs its adapter.
    implementation(libs.flexibleAdapter)
    // Its layout is one of Mihon's, so the generated binding comes from :app and no view binding
    // is needed here — but the views in it are Material Components, a different artifact from Compose.
    implementation(libs.material)
}
