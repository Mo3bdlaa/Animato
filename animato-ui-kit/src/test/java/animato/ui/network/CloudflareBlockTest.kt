package animato.ui.network

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Telling a Cloudflare wall apart from every other way a request can fail.
 *
 * The match has to be on the message, because Mihon's `CloudflareBypassException` is `private` and
 * what escapes is a bare `IOException` — the same type a timeout and a dead host produce. So the
 * question this answers is: does it still say no to those?
 */
@Execution(ExecutionMode.CONCURRENT)
class CloudflareBlockTest {

    private val message = "Failed to bypass Cloudflare"

    @Test
    fun `the cloudflare failure is recognised`() {
        CloudflareBlock.isBlocked(message, IOException(message)) shouldBe true
    }

    @Test
    fun `it is recognised through the wrappers a screen model adds`() {
        val wrapped = IllegalStateException("Failed to load", RuntimeException("source", IOException(message)))

        CloudflareBlock.isBlocked(message, wrapped) shouldBe true
    }

    @Test
    fun `other network failures are not it`() {
        // The point of the whole class: these are the same type, and offering to open a browser
        // window at someone whose wifi dropped is worse than saying nothing.
        CloudflareBlock.isBlocked(message, SocketTimeoutException("timeout")) shouldBe false
        CloudflareBlock.isBlocked(message, IOException("Unable to resolve host")) shouldBe false
        CloudflareBlock.isBlocked(message, null) shouldBe false
    }

    @Test
    fun `the message alone is not enough without the type`() {
        CloudflareBlock.isBlocked(message, IllegalStateException(message)) shouldBe false
    }

    @Test
    fun `a cause that points at itself does not hang`() {
        // Some libraries build these. Walking it without a guard never returns.
        val looping = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }

        CloudflareBlock.isBlocked(message, looping) shouldBe false
    }

    @Test
    fun `a chain longer than the guard gives up rather than searching forever`() {
        var deep: Throwable = IOException(message)
        repeat(40) { deep = RuntimeException("wrapper", deep) }

        // Not a failure to find it — a refusal to keep looking. Nothing wraps an exception forty
        // times, and a chain that long is a sign of something else being wrong.
        CloudflareBlock.isBlocked(message, deep) shouldBe false
    }
}
