plugins {
    alias(mihonx.plugins.android.library)
    alias(mihonx.plugins.spotless)

    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "animato.data"

    sqldelight {
        databases {
            // The generated package is unchanged from Aniyomi's, so every existing query type
            // still resolves under the same name.
            create("AnimeDatabase") {
                packageName.set("tachiyomi.mi.data")
                dialect(libs.sqldelight.sqliteDialect338)
                schemaOutputDirectory.set(project.file("./src/main/sqldelightanime"))
                srcDirs.from(project.file("./src/main/sqldelightanime"))

                /*
                 * As Mihon's :data does — and for more than keeping in step. The androidx driver
                 * over bundled SQLite accepts only an async schema, and bundled SQLite is what
                 * makes these queries mean the same thing on every Android version.
                 *
                 * On the device's own SQLite they did not. `minSdk` is 26; `animehistory.sq`,
                 * `animesources.sq` and `extension_store.sq` upsert with ON CONFLICT … DO UPDATE,
                 * and SQLite only learned that in 3.24 — Android 10. On 8 and 9 those three were a
                 * syntax error at execution, and the first runs every time an episode is watched.
                 */
                generateAsync.set(true)
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    // The anime repositories share column adapters and paging helpers with Mihon's data module.
    api(projects.data)

    implementation(projects.anime.domain)
    implementation(projects.anime.sourceApi)
    implementation(projects.domain)
    implementation(projects.sourceApi)
    implementation(projects.core.common)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.injekt)

    api(libs.bundles.sqldelight)
}
