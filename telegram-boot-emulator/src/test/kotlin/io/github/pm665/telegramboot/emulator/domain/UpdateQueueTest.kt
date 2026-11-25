package io.github.pm665.telegramboot.emulator.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class UpdateQueueTest {
    private val queue = UpdateQueue(Clock.systemUTC())
    private val long = Duration.ofSeconds(10)

    @Test
    fun `update ids increase and pending updates are returned up to the limit`() {
        repeat(3) { push() }

        assertThat(queue.poll(0, 2, Duration.ZERO).map { it.updateId }).containsExactly(1, 2)
        assertThat(queue.poll(0, 100, Duration.ZERO).map { it.updateId }).containsExactly(1, 2, 3)
    }

    @Test
    fun `offset confirms earlier updates and a restarted session gets only unconfirmed ones`() {
        repeat(3) { push() }

        assertThat(queue.poll(3, 100, Duration.ZERO).map { it.updateId }).containsExactly(3)
        push()
        // a restarted BotSession polls from offset 1 again
        assertThat(queue.poll(1, 100, Duration.ZERO).map { it.updateId }).containsExactly(3, 4)
    }

    @Test
    fun `poll waits for the next update`() {
        val poll = pollAsync(long)
        Thread.sleep(200)
        assertThat(poll).isNotDone()
        assertThat(queue.isPolling(Duration.ZERO)).isTrue()

        push()

        assertThat(poll.get(5, TimeUnit.SECONDS).map { it.updateId }).containsExactly(1)
    }

    @Test
    fun `poll returns no updates after the timeout`() {
        val started = System.nanoTime()

        assertThat(queue.poll(0, 100, Duration.ofMillis(300))).isEmpty()
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(300))
    }

    @Test
    fun `new poll releases the waiting one`() {
        val first = pollAsync(long)
        Thread.sleep(200)

        val second = pollAsync(long)

        assertThat(first.get(5, TimeUnit.SECONDS)).isEmpty()
        push()
        assertThat(second.get(5, TimeUnit.SECONDS).map { it.updateId }).containsExactly(1)
    }

    @Test
    fun `closing releases waiting polls`() {
        val poll = pollAsync(long)
        Thread.sleep(200)

        queue.close()

        assertThat(poll.get(5, TimeUnit.SECONDS)).isEmpty()
        assertThat(queue.poll(0, 100, long)).isEmpty()
    }

    @Test
    fun `bot is online while polling and shortly after`() {
        assertThat(queue.isPolling(Duration.ofSeconds(3))).isFalse()

        queue.poll(0, 100, Duration.ZERO)

        assertThat(queue.isPolling(Duration.ofSeconds(3))).isTrue()
        Thread.sleep(50)
        assertThat(queue.isPolling(Duration.ofMillis(10))).isFalse()
    }

    private fun push() = queue.push { Update().apply { updateId = it } }

    private fun pollAsync(timeout: Duration) = CompletableFuture.supplyAsync { queue.poll(0, 100, timeout) }
}
