package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.botapi.BotApiHandler
import io.github.pm665.telegramboot.emulator.domain.BotIdentityResolver
import io.github.pm665.telegramboot.emulator.domain.TelegramEmulator
import io.github.pm665.telegramboot.emulator.server.EmulatorJson
import io.github.pm665.telegramboot.emulator.server.EmulatorRouter
import io.github.pm665.telegramboot.emulator.server.TelegramEmulatorServer
import io.github.pm665.telegramboot.emulator.userapi.UserApiHandler
import io.github.pm665.telegramboot.ports.BotsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.telegram.telegrambots.meta.TelegramUrl

/**
 * Replaces the Telegram Bot API with a local emulator when `telegram-boot.emulator.enabled=true`.
 *
 * Runs before the Telegram auto-configuration so that its [TelegramUrl] bean wins. Since that bean depends on the
 * started server, the emulator is listening before any bot is registered for polling.
 */
@AutoConfiguration(beforeName = ["io.github.pm665.telegramboot.TelegramBootTelegramAutoConfiguration"])
@ConditionalOnClass(TelegramUrl::class)
@ConditionalOnProperty(prefix = TelegramEmulatorProperties.PREFIX, name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(TelegramEmulatorProperties::class)
class TelegramBootEmulatorAutoConfiguration {
    private val log = LoggerFactory.getLogger(TelegramBootEmulatorAutoConfiguration::class.java)

    @Bean(destroyMethod = "close")
    fun telegramEmulator(
        properties: TelegramEmulatorProperties,
        botsService: ObjectProvider<BotsService>,
    ): TelegramEmulator =
        TelegramEmulator(BotIdentityResolver({ botsService.getIfUnique() }, properties.bots.associate { it.token to it.username }))

    @Bean(destroyMethod = "stop")
    fun telegramEmulatorServer(
        properties: TelegramEmulatorProperties,
        emulator: TelegramEmulator,
        environment: ConfigurableEnvironment,
    ): TelegramEmulatorServer {
        val mapper = EmulatorJson.mapper()
        val server = TelegramEmulatorServer(properties.host, properties.port, EmulatorRouter(BotApiHandler(emulator, mapper), UserApiHandler(emulator, mapper), mapper))
        server.start()
        environment.propertySources.addFirst(MapPropertySource(LOCAL_PORT_SOURCE, mapOf(LOCAL_PORT_PROPERTY to server.port)))
        log.warn(
            "Telegram emulator is enabled: bots are NOT connected to Telegram. Bot API and UI: {}",
            server.baseUrl,
        )
        if (environment.containsProperty(TELEGRAM_API_URL_PROPERTY)) {
            log.warn("{} is ignored while the Telegram emulator is enabled", TELEGRAM_API_URL_PROPERTY)
        }
        return server
    }

    @Bean
    fun telegramEmulatorApiUrl(server: TelegramEmulatorServer): TelegramUrl = TelegramUrl("http", server.clientHost, server.port, false)

    companion object {
        const val LOCAL_PORT_PROPERTY = "local.telegram-boot.emulator.port"
        private const val LOCAL_PORT_SOURCE = "telegramEmulatorLocalPort"
        private const val TELEGRAM_API_URL_PROPERTY = "telegram-boot.telegram.api-url"
    }
}
