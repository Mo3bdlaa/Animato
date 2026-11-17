package animato.app

import io.github.mo3bdlaa.animato.BuildConfig

/**
 * Whether this build is an alpha. Alphas carry a suffixed version (0.1.0-alpha.106), a final release
 * a bare one; debug builds count as alpha.
 *
 * The curated lists of third-party stores and Stremio addons are offered only in alphas. A final
 * release adds a store or an addon by the address somebody brings.
 */
object ReleaseChannel {
    val isAlpha: Boolean = BuildConfig.DEBUG || BuildConfig.VERSION_NAME.contains('-')
}
