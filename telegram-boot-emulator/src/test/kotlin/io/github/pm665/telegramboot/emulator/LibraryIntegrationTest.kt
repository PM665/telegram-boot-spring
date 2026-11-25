package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.TelegramBootTelegramAutoConfiguration
import io.github.pm665.telegramboot.core.autoconfigure.TelegramBootCoreAutoConfiguration
import io.github.pm665.telegramboot.domain.AccessType
import io.github.pm665.telegramboot.domain.BotActionContext
import io.github.pm665.telegramboot.domain.actions.ActionResult
import io.github.pm665.telegramboot.domain.actions.SendMessageMarkup
import io.github.pm665.telegramboot.domain.annotations.BotAction
import io.github.pm665.telegramboot.domain.annotations.BotCommand
import io.github.pm665.telegramboot.emulator.domain.Button
import io.github.pm665.telegramboot.emulator.domain.HistoryAction
import io.github.pm665.telegramboot.emulator.domain.HistoryEntry
import io.github.pm665.telegramboot.emulator.domain.Party
import io.github.pm665.telegramboot.emulator.domain.TelegramEmulator
import io.github.pm665.telegramboot.emulator.domain.UserProfile
import io.github.pm665.telegramboot.events.BotStateChangedEvent
import io.github.pm665.telegramboot.features.bots.InMemoryBotsService
import io.github.pm665.telegramboot.ports.BotsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import java.time.Duration
import java.time.Instant

/** The real library bots, started by the application context, talk to the emulator. */
class LibraryIntegrationTest {
    private val botsService = InMemoryBotsService().apply { register("123:fake", "fake_bot") }
    private val alice = UserProfile(4242, "alice", "Alice", "en")

    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    TelegramBootCoreAutoConfiguration::class.java,
                    TelegramBootTelegramAutoConfiguration::class.java,
                    TelegramBootEmulatorAutoConfiguration::class.java,
                ),
            ).withPropertyValues("telegram-boot.emulator.enabled=true", "telegram-boot.emulator.port=0")
            .withBean(BotsService::class.java, { botsService })
            .withBean(HelloCommand::class.java)

    @Test
    fun `bot of the application is registered on start and serves the user`() {
        runner.run { context ->
            val emulator = context.getBean(TelegramEmulator::class.java)
            await { emulator.bots().singleOrNull()?.online == true }
            assertThat(emulator.bots().single().username).isEqualTo("fake_bot")

            emulator.startChat("fake_bot", alice)

            val menu = awaitEntry(emulator, "fake_bot") { it.from == Party.BOT && it.action == HistoryAction.RICH_MESSAGE }
            assertThat(menu.text).isEqualTo("Commands")
            assertThat(menu.buttons.flatten()).contains(Button("hello", "/hello"))

            emulator.pressButton("fake_bot", alice.id, alice, menu.messageId!!, "/hello")

            awaitEntry(emulator, "fake_bot") { it.from == Party.BOT && it.text == "Hello alice" }
        }
    }

    @Test
    fun `bot enabled at runtime is registered and serves the user`() {
        runner.run { context ->
            val emulator = context.getBean(TelegramEmulator::class.java)
            await { emulator.bots().any { it.online } }
            val bot = BotsService.Bot("456:later", "later_bot")
            botsService.register(bot)

            context.publishEvent(BotStateChangedEvent(bot, enabled = true))

            await { emulator.bots().any { it.username == "later_bot" && it.online } }
            emulator.startChat("later_bot", alice)
            emulator.sendMessage("later_bot", alice.id, alice, "/hello")
            awaitEntry(emulator, "later_bot") { it.from == Party.BOT && it.text == "Hello alice" }
        }
    }

    private fun awaitEntry(
        emulator: TelegramEmulator,
        bot: String,
        predicate: (HistoryEntry) -> Boolean,
    ): HistoryEntry {
        var found: HistoryEntry? = null
        await {
            found = emulator.history(bot, alice.id, alice.id).firstOrNull(predicate)
            found != null
        }
        return found!!
    }

    private fun await(condition: () -> Boolean) {
        val deadline = Instant.now().plus(Duration.ofSeconds(10))
        while (!condition()) {
            check(Instant.now().isBefore(deadline)) { "Condition was not met in time" }
            Thread.sleep(50)
        }
    }

    @BotCommand(name = "hello", accessType = AccessType.ALL, keepMessage = true)
    class HelloCommand {
        @BotAction
        fun hello(context: BotActionContext): ActionResult =
            ActionResult.builder().messagesToSend(listOf(SendMessageMarkup("Hello ${context.user?.user?.username}"))).build()
    }
}
