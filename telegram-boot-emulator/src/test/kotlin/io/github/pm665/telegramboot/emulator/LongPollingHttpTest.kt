package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.alice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class LongPollingHttpTest {
    private val fixture = EmulatorFixture()

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `getUpdates request is held until a user message arrives`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        val poll = CompletableFuture.supplyAsync { alpha.execute(GetUpdates.builder().timeout(30).build()) }
        Thread.sleep(300)
        assertThat(poll).isNotDone()
        assertThat(fixture.emulator.bots().single().online).isTrue()

        fixture.emulator.startChat(ALPHA, alice)

        assertThat(poll.get(5, TimeUnit.SECONDS).single().message.text).isEqualTo("/start")
    }

    @Test
    fun `stopping the emulator releases waiting requests`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        val poll = CompletableFuture.supplyAsync { runCatching { alpha.execute(GetUpdates.builder().timeout(30).build()) } }
        Thread.sleep(300)

        fixture.close()

        assertThat(poll.get(5, TimeUnit.SECONDS).getOrNull().orEmpty()).isEmpty()
    }
}
