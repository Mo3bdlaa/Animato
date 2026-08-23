/*
 * Kotlin here has to be new enough to read the libraries Animato supplies, not just new enough to
 * compile this source. The runtime dependencies below are compileOnly and come from the app, and
 * kotlinx-serialization 1.11 carries Kotlin 2.3 metadata — an older compiler refuses it outright
 * with "Module was compiled with an incompatible version of Kotlin", before it looks at any code.
 * So this tracks the app's own Kotlin (gradle/libs.versions.toml).
 *
 * AGP does not have to match the app's; it only has to build an APK, and the app's is a major
 * version ahead with breaking DSL changes this template does not need.
 */
plugins {
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.4.10" apply false
}
