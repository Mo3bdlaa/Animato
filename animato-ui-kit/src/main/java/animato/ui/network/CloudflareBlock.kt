package animato.ui.network

import android.content.Context
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.IOException

/**
 * Recognises the failure Cloudflare leaves behind, so something can be offered instead of a dead
 * error message.
 *
 * ## Why this is a string comparison
 *
 * Mihon's `CloudflareInterceptor` throws `CloudflareBypassException`, and that class is
 * **`private`**:
 *
 * ```kotlin
 * catch (e: CloudflareBypassException) {
 *     throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
 * }
 * ```
 *
 * So what escapes is a plain `IOException` — the same type a timeout, a reset connection and a
 * missing host all produce — carrying a translated message as its only distinguishing mark. The
 * cause is a private type nothing outside `:core:common` can name, and the message is the one thing
 * left to match on.
 *
 * It is not as fragile as it looks. The string comes from the same resource in the same process, so
 * it cannot disagree about the locale, and if Mihon ever rewords it the match simply stops matching
 * — the prompt disappears, and nothing breaks.
 *
 * ## Why bother at all
 *
 * Because the fix already exists and nobody is ever pointed at it. Mihon's interceptor tries to
 * solve the challenge in a WebView that is **never attached to a window**, so it can only pass
 * challenges that solve themselves; the modern interactive one wants a tap on a visible checkbox,
 * and an invisible WebView cannot be tapped. What it *can* do is fail, once every thirty seconds,
 * forever.
 *
 * Meanwhile `WebViewScreen` shows a real, visible WebView with the very same user agent
 * (`headers["user-agent"] ?: defaultUserAgentProvider()`), and a WebView writes its cookies into
 * Android's process-wide `CookieManager` — which is what `AndroidCookieJar` reads and therefore
 * what every extension, tracker and request already sends.
 *
 * So the whole repair is: notice this failure, and open that screen. The cookie and the matching
 * user agent take care of themselves.
 */
object CloudflareBlock {

    /**
     * Whether [throwable], or anything it wraps, is Cloudflare turning us away.
     */
    fun isBlocked(context: Context, throwable: Throwable?): Boolean =
        isBlocked(context.stringResource(MR.strings.information_cloudflare_bypass_failure), throwable)

    /**
     * The same question with the message handed in, which is the whole of the logic and the only
     * part worth testing — a JVM test has no `Context` to read a string resource from.
     *
     * The cause chain is walked because callers rarely see the original: a repository or a screen
     * model will have wrapped it several times over by the time it reaches anywhere with a person
     * in front of it.
     */
    internal fun isBlocked(message: String, throwable: Throwable?): Boolean {
        var current = throwable
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current is IOException && current.message == message) return true
            current = current.cause.takeIf { it !== current }
            depth++
        }
        return false
    }

    /**
     * Guards against a cause chain that points back into itself, which some libraries produce and
     * which would otherwise hang here rather than fail.
     */
    private const val MAX_CAUSE_DEPTH = 16
}
