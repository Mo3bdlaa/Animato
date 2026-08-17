package animato.anime.player

import android.app.Application
import eu.kanade.tachiyomi.animesource.AnimeSource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A sentence for the failures whose own words say nothing.
 *
 * The push to surface real errors instead of swallowing them worked — a device promptly returned
 * one reading *"Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()' on
 * a null object reference."* That is R8's doing: extensions ship minified, and the shrinker
 * rewrites Kotlin's null-check intrinsics into the cheaper `getClass()` idiom, which replaces a
 * named, readable assertion with that sentence. It reaches the screen exactly when an extension
 * trips over a site that changed shape underneath it.
 *
 * So the failures with well-known shapes get said in words: an extension crashing inside itself,
 * a source that is not installed, a site that cannot be reached, a site that never answered.
 * Anything else returns null and the caller shows the exception's own message, which for an HTTP
 * 403 or a Cloudflare page is already the most informative thing available.
 *
 * Not for [AnimeSource] failures alone — the manga half's stub throws by the same name — which is
 * why the not-installed match is by simple name rather than by class.
 */
fun Throwable.describeForUser(): String? {
    val resource = when {
        this is UnknownHostException -> AYMR.strings.failure_site_unreachable
        this is SocketTimeoutException -> AYMR.strings.failure_site_timed_out
        javaClass.simpleName == "AnimeSourceNotInstalledException" ||
            javaClass.simpleName == "SourceNotInstalledException" -> AYMR.strings.failure_source_not_installed
        this is NullPointerException ||
            message?.contains("on a null object reference") == true -> AYMR.strings.failure_extension_crashed
        else -> return null
    }
    return Injekt.get<Application>().stringResource(resource)
}
