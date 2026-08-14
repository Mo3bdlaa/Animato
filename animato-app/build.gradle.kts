plugins {
    alias(mihonx.plugins.android.application)
    alias(mihonx.plugins.compose)
}

android {
    namespace = "io.github.mo3bdlaa.animato"

    defaultConfig {
        applicationId = "io.github.mo3bdlaa.animato"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Mihon, consumed as a library. Nothing in this module edits it.
    implementation(projects.app)
}
