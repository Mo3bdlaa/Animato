package aniyomi.core.common.torrent

import animato.anime.device.DeviceMemory
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class StreamingBudgetTest {

    private val low = TorrentServerApi.StreamingBudget.of(DeviceMemory.Low)
    private val modest = TorrentServerApi.StreamingBudget.of(DeviceMemory.Modest)
    private val roomy = TorrentServerApi.StreamingBudget.of(DeviceMemory.Roomy)

    @Test
    fun `a roomy device keeps what the server was tuned to before`() {
        roomy.cacheBytes shouldBe 192L * 1024 * 1024
        roomy.connections shouldBe 120
        roomy.dhtConnections shouldBe 500
    }

    @Test
    fun `smaller devices spend less, on every axis`() {
        low.cacheBytes shouldBeLessThan modest.cacheBytes
        modest.cacheBytes shouldBeLessThan roomy.cacheBytes
        low.connections shouldBeLessThan modest.connections
        low.dhtConnections shouldBeLessThan modest.dhtConnections
    }

    @Test
    fun `a 2 GB television holds no more than 48 MB of pieces`() {
        low.cacheBytes shouldBeLessThanOrEqual 48L * 1024 * 1024
    }
}
