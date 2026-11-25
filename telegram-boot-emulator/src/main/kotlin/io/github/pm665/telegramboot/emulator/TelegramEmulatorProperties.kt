package io.github.pm665.telegramboot.emulator

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Local Telegram Bot API emulator for development and tests. Never enable it in production:
 * bots are not connected to Telegram while it is on.
 */
@ConfigurationProperties(TelegramEmulatorProperties.PREFIX)
data class TelegramEmulatorProperties(
    /** Starts the emulator and points all bots at it instead of `telegram-boot.telegram.api-url`. */
    val enabled: Boolean = false,
    /** Interface the emulator listens on. Keep it local: the emulator has no authentication. */
    val host: String = "127.0.0.1",
    /** Emulator port, `0` picks a free port (published as `local.telegram-boot.emulator.port`). */
    val port: Int = 18790,
    /** Bots known by token in addition to the application's `BotsService`. */
    val bots: List<StaticBot> = emptyList(),
) {
    init {
        require(host.isNotBlank()) { "$PREFIX.host must not be blank" }
        require(port in 0..65535) { "$PREFIX.port must be between 0 and 65535" }
    }

    data class StaticBot(
        val token: String,
        val username: String,
    ) {
        init {
            require(token.isNotBlank()) { "$PREFIX.bots[].token must not be blank" }
            require(username.isNotBlank()) { "$PREFIX.bots[].username must not be blank" }
        }
    }

    companion object {
        const val PREFIX = "telegram-boot.emulator"
    }
}
