package sample

import io.github.pm665.telegramboot.ports.BotsService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

/**
 * Sample telegram-boot application. Run it with the `emulator` profile to chat with its bots locally,
 * without Telegram: the emulator UI is available at http://localhost:18790.
 */
@SpringBootApplication
@EnableConfigurationProperties(SampleBotsProperties::class)
class SampleApplication {
    /** The library starts every bot returned by the [BotsService]; here they come from the configuration. */
    @Bean
    fun botsService(properties: SampleBotsProperties): BotsService =
        object : BotsService {
            private val bots = properties.bots.map { BotsService.Bot(it.token, it.username.removePrefix("@")) }

            override fun findAllActiveBots() = bots

            override fun findBotByBotUsername(botUsername: String) = bots.firstOrNull { it.botUsername == botUsername }
        }
}

@ConfigurationProperties("sample")
data class SampleBotsProperties(
    val bots: List<BotProperties> = emptyList(),
) {
    data class BotProperties(
        val username: String,
        val token: String,
    )
}

fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}
