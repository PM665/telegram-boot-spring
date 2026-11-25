package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.botapi.BotApiHandler
import io.github.pm665.telegramboot.emulator.domain.BotIdentityResolver
import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import io.github.pm665.telegramboot.emulator.domain.HistoryEntry
import io.github.pm665.telegramboot.emulator.domain.TelegramEmulator
import io.github.pm665.telegramboot.emulator.domain.UserProfile
import io.github.pm665.telegramboot.emulator.server.EmulatorJson
import io.github.pm665.telegramboot.emulator.server.EmulatorRouter
import io.github.pm665.telegramboot.emulator.server.TelegramEmulatorServer
import io.github.pm665.telegramboot.emulator.userapi.UserApiHandler
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.TelegramUrl
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

/** Emulator on a random port with two statically configured bots, driven by the real telegrambots client. */
class EmulatorFixture : AutoCloseable {
    val emulator = TelegramEmulator(BotIdentityResolver({ null }, mapOf(ALPHA_TOKEN to ALPHA, BETA_TOKEN to BETA)))
    private val mapper = EmulatorJson.mapper()
    val server = TelegramEmulatorServer("127.0.0.1", 0, EmulatorRouter(BotApiHandler(emulator, mapper), UserApiHandler(emulator, mapper), mapper)).apply { start() }
    private val url = TelegramUrl("http", "127.0.0.1", server.port, false)

    fun client(token: String) = OkHttpTelegramClient(token, url)

    /** Client of a bot registered with getMe, as BotManager does on start. */
    fun registeredClient(token: String) = client(token).also { it.execute(GetMe()) }

    fun updates(
        client: OkHttpTelegramClient,
        offset: Int = 0,
    ): List<Update> = client.execute(GetUpdates.builder().offset(offset).timeout(0).build())

    fun history(
        user: UserProfile,
        bot: String = ALPHA,
    ): List<HistoryEntry> = emulator.history(bot, user.id, user.id)

    override fun close() {
        server.stop()
        emulator.close()
    }

    companion object {
        const val ALPHA = "alpha_bot"
        const val ALPHA_TOKEN = "111:alpha"
        const val BETA = "beta_bot"
        const val BETA_TOKEN = "222:beta"

        val alice = UserProfile(4242, "alice", "Alice", "en")
        val bob = UserProfile(4343, "bob", "Bob", "de")

        /** Bot API error returned to the telegrambots client. */
        fun apiError(call: () -> Any?): TelegramApiRequestException {
            try {
                call()
            } catch (e: TelegramApiRequestException) {
                return e
            }
            throw AssertionError("Bot API request was expected to fail")
        }

        /** Error of a user action. */
        fun userError(call: () -> Any?): EmulatorException {
            try {
                call()
            } catch (e: EmulatorException) {
                return e
            }
            throw AssertionError("User action was expected to fail")
        }
    }
}
