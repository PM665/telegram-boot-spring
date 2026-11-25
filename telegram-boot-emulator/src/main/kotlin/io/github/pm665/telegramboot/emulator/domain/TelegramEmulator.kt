package io.github.pm665.telegramboot.emulator.domain

import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory Telegram: the registry of emulated bots and the entry point for user actions.
 *
 * A bot is registered by the `getMe` request of its token; every other Bot API request requires a registered token.
 */
class TelegramEmulator(
    private val identityResolver: BotIdentityResolver,
    private val clock: Clock = Clock.systemUTC(),
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(TelegramEmulator::class.java)
    private val botsByUsername = ConcurrentHashMap<String, EmulatedBot>()
    private val botsByToken = ConcurrentHashMap<String, EmulatedBot>()

    internal fun register(token: String): EmulatedBot {
        val username = identityResolver.username(token) ?: throw EmulatorException.unauthorized()
        synchronized(this) {
            val bot = botsByUsername[username.lowercase()]
            if (bot == null) {
                return EmulatedBot(botId(token), username, token, clock.instant(), clock).also {
                    botsByUsername[username.lowercase()] = it
                    botsByToken[token] = it
                    log.info("Telegram emulator registered bot @{}", username)
                }
            }
            if (bot.token != token || bot.username != username) {
                botsByToken.remove(bot.token)
                bot.rename(username, token)
                botsByToken[token] = bot
            }
            return bot
        }
    }

    internal fun registeredBot(token: String): EmulatedBot =
        botsByToken[token] ?: throw EmulatorException.unauthorized("bot is not registered in the emulator, getMe must be called first")

    fun bots(): List<BotSummary> = botsByUsername.values.map { it.summary() }.sortedBy { it.username.lowercase() }

    fun startChat(
        botUsername: String,
        user: UserProfile,
    ): StartChatResult = bot(botUsername).startChat(user)

    fun chats(
        botUsername: String,
        userId: Long,
    ): List<ChatSummary> = bot(botUsername).chats(userId)

    fun sendMessage(
        botUsername: String,
        chatId: Long,
        user: UserProfile,
        text: String,
    ): UserActionResult = bot(botUsername).sendUserMessage(chatId, user, text)

    fun pressButton(
        botUsername: String,
        chatId: Long,
        user: UserProfile,
        messageId: Int,
        callbackData: String,
    ): UserActionResult = bot(botUsername).pressButton(chatId, user, messageId, callbackData)

    fun history(
        botUsername: String,
        chatId: Long,
        userId: Long,
        afterSeq: Long = 0,
    ): List<HistoryEntry> = bot(botUsername).history(chatId, userId, afterSeq)

    /** Releases waiting `getUpdates` requests. */
    override fun close() = botsByUsername.values.forEach(EmulatedBot::close)

    private fun bot(username: String): EmulatedBot =
        botsByUsername[username.removePrefix("@").lowercase()] ?: throw EmulatorException.notFound("bot @$username is not registered")

    private companion object {
        /** Bot id is the numeric part of the token, as in Telegram. */
        fun botId(token: String): Long =
            token.substringBefore(':').toLongOrNull()?.takeIf { it > 0 } ?: ((token.hashCode().toLong() and 0x7fffffff) + 1)
    }
}
