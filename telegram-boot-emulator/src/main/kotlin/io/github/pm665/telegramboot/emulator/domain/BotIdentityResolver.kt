package io.github.pm665.telegramboot.emulator.domain

import io.github.pm665.telegramboot.domain.bot.BotPlatform
import io.github.pm665.telegramboot.ports.BotsService

/**
 * Resolves the username of a bot token, as Telegram knows it from BotFather: first from the application's
 * [BotsService] (looked up lazily, bots may be toggled at runtime), then from the statically configured bots.
 */
class BotIdentityResolver(
    private val botsService: () -> BotsService?,
    private val staticBots: Map<String, String> = emptyMap(),
) {
    fun username(token: String): String? =
        botsService()
            ?.findAllActiveBots()
            ?.firstOrNull { it.platform == BotPlatform.TELEGRAM && it.token == token }
            ?.botUsername
            ?.removePrefix("@")
            ?: staticBots[token]?.removePrefix("@")
}
