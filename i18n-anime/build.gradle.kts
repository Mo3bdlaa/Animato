import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(mihonx.plugins.kotlin.multiplatform)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.moko.resources)
}

kotlin {
    android {
        namespace = "tachiyomi.i18n.aniyomi"

        lint {
            disable.addAll(listOf("MissingTranslation", "ExtraTranslation"))
        }
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    dependencies {
        api(libs.moko.resources)
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

multiplatformResources {
    // AYMR is the name every anime string in the ported UI is already written against.
    resourcesClassName.set("AYMR")
    resourcesPackage.set("tachiyomi.i18n.aniyomi")
}
