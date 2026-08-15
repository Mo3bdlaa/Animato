pluginManagement {
    includeBuild("gradle/build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://www.jitpack.io")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("animato") {
            from(files("gradle/animato.versions.toml"))
        }
        create("mihonx") {
            from(files("gradle/mihon.versions.toml"))
        }
    }

    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://www.jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Animato"
include(":anime:data")
include(":anime:domain")
include(":anime:source-api")
include(":anime:source-local")
include(":app")
// probe: the baseline profile module consumes :app as an application
// include(":baseline-profile")
include(":core-metadata")
include(":core:archive")
include(":core:common")
include(":core:viewmodel")
include(":data")
include(":domain")
include(":i18n")
include(":presentation-core")
include(":presentation-widget")
include(":source-api")
include(":source-local")
include(":telemetry")
include(":animato-app")
include(":animato-ui-kit")
include(":i18n-anime")
include(":anime:player")
include(":anime:services")
include(":anime:ui")
