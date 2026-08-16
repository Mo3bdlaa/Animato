package animato.app.sync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The filename is the whole protocol.
 *
 * A device decides whether to download a backup from the folder listing alone — it never opens a
 * file to find out whose it is — so a name that parses wrongly is a device merging its own snapshot
 * over itself, or never seeing its other phone at all.
 */
@Execution(ExecutionMode.CONCURRENT)
class SyncFileTest {

    @Test
    fun `a name written here is read back the same`() {
        val original = SyncFile(deviceId = "a1b2c3d4", writtenAt = 1_724_000_000_000)
        SyncFile.parse(original.fileName()) shouldBe original
    }

    @Test
    fun `the timestamp is taken from the name and not from the file`() {
        SyncFile.parse("animato-sync_abc_1700000000000.tachibk") shouldBe
            SyncFile("abc", 1_700_000_000_000)
    }

    @Test
    fun `a device id containing the separator still round-trips`() {
        // Only the last underscore separates, so an id is free to contain one. Splitting on the
        // first would read the id as "my" and the rest as a timestamp that does not parse — and the
        // file would then be silently ignored rather than reported.
        val original = SyncFile(deviceId = "my_phone", writtenAt = 1_700_000_000_000)
        SyncFile.parse(original.fileName()) shouldBe original
    }

    @Test
    fun `anything that is not one of ours is not one of ours`() {
        // The folder is shared, so it holds other apps' files and the user's own. Every one of these
        // has to come back null rather than throw, because they are read on every single run.
        SyncFile.parse("io.github.mo3bdlaa.animato_2026-08-16_10-00.tachibk") shouldBe null
        SyncFile.parse("animato-sync_abc.tachibk") shouldBe null
        SyncFile.parse("animato-sync_abc_notanumber.tachibk") shouldBe null
        SyncFile.parse("animato-sync_abc_123.txt") shouldBe null
        SyncFile.parse("holiday.jpg") shouldBe null
        SyncFile.parse("") shouldBe null
    }
}
