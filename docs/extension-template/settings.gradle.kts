// A standalone project: nothing here is part of Animato's build. Copy this directory out into a
// repository of its own — see README.md.
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Where the extension stubs live. See the note on the dependency in build.gradle.kts.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "animato-extensions"

// One module per source. Add a line for each new one.
include(":src:example")
include(":src:shahedpro")
