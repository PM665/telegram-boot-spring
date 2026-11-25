package io.github.pm665.telegramboot.emulator.domain

import org.telegram.telegrambots.meta.api.objects.Update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Pending updates of one bot with `getUpdates` long polling semantics:
 * update ids increase monotonically, an `offset` confirms (drops) all updates below it,
 * and a new poll releases the previous one with an empty result (a restarted bot session replaces the old one).
 */
internal class UpdateQueue(
    private val clock: Clock,
) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val pending = ArrayDeque<Update>()
    private var lastUpdateId = 0
    private var generation = 0L
    private var activePolls = 0
    private var lastPollAt: Instant? = null
    private var closed = false

    fun push(update: (updateId: Int) -> Update): Int =
        lock.withLock {
            val updateId = ++lastUpdateId
            pending.addLast(update(updateId))
            changed.signalAll()
            updateId
        }

    fun poll(
        offset: Int,
        limit: Int,
        timeout: Duration,
    ): List<Update> =
        lock.withLock {
            val poll = ++generation
            changed.signalAll()
            confirm(offset)
            activePolls++
            lastPollAt = clock.instant()
            try {
                awaitUpdates(poll, limit, timeout)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                emptyList()
            } finally {
                activePolls--
                lastPollAt = clock.instant()
            }
        }

    /** A bot is online while it waits in `getUpdates` or has just returned from it. */
    fun isPolling(grace: Duration): Boolean =
        lock.withLock {
            activePolls > 0 || lastPollAt?.let { Duration.between(it, clock.instant()) <= grace } == true
        }

    fun close() =
        lock.withLock {
            closed = true
            changed.signalAll()
        }

    private fun awaitUpdates(
        poll: Long,
        limit: Int,
        timeout: Duration,
    ): List<Update> {
        var remaining = timeout.toNanos()
        while (!closed && generation == poll) {
            val ready = pending.take(limit)
            if (ready.isNotEmpty() || remaining <= 0) return ready
            remaining = changed.awaitNanos(remaining)
        }
        return emptyList()
    }

    private fun confirm(offset: Int) {
        if (offset <= 0) return
        while (pending.firstOrNull()?.updateId?.let { it < offset } == true) pending.removeFirst()
    }
}
