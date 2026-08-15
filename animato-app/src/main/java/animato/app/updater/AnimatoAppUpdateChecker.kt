package animato.app.updater

import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import io.github.mo3bdlaa.animato.BuildConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.model.Release
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Asks this repository whether there is a newer Animato than the one running.
 *
 * Mihon's `AppUpdateChecker` cannot be pointed at us, for two reasons that are both about the
 * shape of our releases rather than the name in the URL:
 *
 * - it reads `/releases/latest`, and GitHub's "latest" *excludes prereleases* — every Animato
 *   release so far is one, so that endpoint answers with nothing at all;
 * - its comparison cannot order a prerelease tag. [SemanticVersion] says why.
 *
 * What it decides, and what it deliberately does not:
 *
 * **Whether a prerelease counts** is read off the running build rather than a setting. A build
 * whose own version has a prerelease part — an alpha — is offered alphas; a plain `0.1.0` build is
 * offered only finished releases. Someone who installed an alpha keeps getting alphas, and nobody
 * is dragged onto one by an update prompt.
 *
 * **Which file to offer** comes from [Build.SUPPORTED_ABIS] in its own order, so a 64-bit phone is
 * offered the 64-bit APK even though it could run the 32-bit one. A release carrying nothing this
 * device can install is not an update: it is skipped rather than offered a download that would
 * fail at the installer.
 *
 * The download and the install are Mihon's `NewUpdateScreen` — repository-agnostic, taking a link.
 */
class AnimatoAppUpdateChecker(
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    /**
     * The release to offer, or null when the running build is already the newest one.
     */
    suspend fun checkForUpdate(): Release? = withIOContext {
        val installed = SemanticVersion.parse(BuildConfig.VERSION_NAME) ?: return@withIOContext null

        val releases = with(json) {
            network.client
                .newCall(GET("$GITHUB_API/repos/$RELEASE_REPO/releases?per_page=$PAGE_SIZE"))
                .awaitSuccess()
                .parseAs<List<GithubReleaseSummary>>()
        }

        newerRelease(releases, installed, Build.SUPPORTED_ABIS.asList())
    }

    companion object {
        private const val GITHUB_API = "https://api.github.com"
        private const val PAGE_SIZE = 30

        val RELEASE_REPO: String get() = BuildConfig.ANIMATO_RELEASE_REPO

        val RELEASE_URL: String get() = "https://github.com/$RELEASE_REPO/releases/tag/v${BuildConfig.VERSION_NAME}"
    }
}

/**
 * The newest release [installed] should be offered, or null if there is none.
 *
 * Separate from the request so it can be tested without one — the ordering rules here are the part
 * that can be wrong quietly.
 */
internal fun newerRelease(
    releases: List<GithubReleaseSummary>,
    installed: SemanticVersion,
    abis: List<String>,
): Release? {
    return releases
        .asSequence()
        .filterNot { it.isDraft }
        // An alpha is offered alphas; a finished build is offered only finished releases.
        .filter { installed.isPrerelease || !it.isPrerelease }
        .mapNotNull { release -> SemanticVersion.parse(release.tag)?.let { it to release } }
        .filter { (version, _) -> version > installed }
        .sortedByDescending { (version, _) -> version }
        // Not `firstOrNull` on the newest alone: if the newest release has no APK for this device,
        // the one before it may, and that is still an update.
        .firstNotNullOfOrNull { (version, release) ->
            release.downloadLinkFor(abis)?.let { link ->
                Release(
                    version = version.toString(),
                    info = release.info.trim(),
                    releaseLink = release.releaseLink,
                    downloadLink = link,
                )
            }
        }
}

/**
 * A GitHub release, cut down to what the updater decides with.
 *
 * Mihon's `GithubRelease` is nearly this, and is not reused because it carries neither `draft` nor
 * `prerelease` — the two fields that matter most here, since asking for the list rather than for
 * "latest" means GitHub no longer filters either one for us.
 */
@Serializable
internal data class GithubReleaseSummary(
    @SerialName("tag_name")
    val tag: String,
    @SerialName("body")
    val info: String = "",
    @SerialName("html_url")
    val releaseLink: String,
    @SerialName("draft")
    val isDraft: Boolean = false,
    @SerialName("prerelease")
    val isPrerelease: Boolean = false,
    val assets: List<GithubReleaseAsset> = emptyList(),
) {

    /**
     * The APK for the first architecture this device names, which is its preferred one.
     */
    fun downloadLinkFor(abis: List<String>): String? = abis.firstNotNullOfOrNull { abi ->
        assets.firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }?.downloadLink
    }
}

@Serializable
internal data class GithubReleaseAsset(
    val name: String,
    @SerialName("browser_download_url")
    val downloadLink: String,
)
