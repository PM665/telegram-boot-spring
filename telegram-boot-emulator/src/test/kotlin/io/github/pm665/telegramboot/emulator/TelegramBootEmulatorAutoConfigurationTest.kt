package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.TelegramBootTelegramAutoConfiguration
import io.github.pm665.telegramboot.core.autoconfigure.TelegramBootCoreAutoConfiguration
import io.github.pm665.telegramboot.emulator.server.TelegramEmulatorServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.telegram.telegrambots.meta.TelegramUrl
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class TelegramBootEmulatorAutoConfigurationTest {
    // Telegram auto-configuration is listed first on purpose: the emulator must still be applied before it.
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    TelegramBootCoreAutoConfiguration::class.java,
                    TelegramBootTelegramAutoConfiguration::class.java,
                    TelegramBootEmulatorAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `emulator is disabled by default and bots use the configured telegram api url`() {
        runner
            .withPropertyValues("telegram-boot.telegram.api-url=http://localhost:8081")
            .run { context ->
                assertThat(context).doesNotHaveBean(TelegramEmulatorServer::class.java)
                assertThat(context.getBean(TelegramUrl::class.java)).isEqualTo(TelegramUrl("http", "localhost", 8081, false))
                assertThat(context.environment.containsProperty(TelegramBootEmulatorAutoConfiguration.LOCAL_PORT_PROPERTY)).isFalse()
            }
    }

    @Test
    fun `enabled emulator is listening and replaces the telegram api url`() {
        runner
            .withPropertyValues(
                "telegram-boot.emulator.enabled=true",
                "telegram-boot.emulator.port=0",
                "telegram-boot.telegram.api-url=http://localhost:8081",
            ).run { context ->
                val server = context.getBean(TelegramEmulatorServer::class.java)
                assertThat(context).hasSingleBean(TelegramUrl::class.java)
                assertThat(context.getBean(TelegramUrl::class.java)).isEqualTo(TelegramUrl("http", "127.0.0.1", server.port, false))
                assertThat(context.environment.getProperty(TelegramBootEmulatorAutoConfiguration.LOCAL_PORT_PROPERTY, Int::class.java))
                    .isEqualTo(server.port)
                val unknownBot = get("${server.baseUrl}/bot123:unknown/getMe")
                assertThat(unknownBot.statusCode()).isEqualTo(401)
                assertThat(unknownBot.body()).isEqualTo("""{"ok":false,"error_code":401,"description":"Unauthorized"}""")
                val bots = get("${server.baseUrl}/api/bots")
                assertThat(bots.statusCode()).isEqualTo(200)
                assertThat(bots.body()).isEqualTo("[]")
            }
    }

    @Test
    fun `emulator releases its port when the context is closed`() {
        var baseUrl = ""
        runner
            .withPropertyValues("telegram-boot.emulator.enabled=true", "telegram-boot.emulator.port=0")
            .run { context -> baseUrl = context.getBean(TelegramEmulatorServer::class.java).baseUrl }

        assertThatThrownBy { get("$baseUrl/") }.isInstanceOf(ConnectException::class.java)
    }

    @Test
    fun `static bots are bound from properties`() {
        runner
            .withPropertyValues(
                "telegram-boot.emulator.enabled=true",
                "telegram-boot.emulator.port=0",
                "telegram-boot.emulator.bots[0].token=123:fake",
                "telegram-boot.emulator.bots[0].username=fake_bot",
            ).run { context ->
                assertThat(context.getBean(TelegramEmulatorProperties::class.java).bots)
                    .containsExactly(TelegramEmulatorProperties.StaticBot("123:fake", "fake_bot"))
            }
    }

    @Test
    fun `invalid emulator port fails the startup`() {
        runner
            .withPropertyValues("telegram-boot.emulator.enabled=true", "telegram-boot.emulator.port=70000")
            .run { context -> assertThat(context).hasFailed() }
    }

    @Test
    fun `application provided TelegramUrl replaces the one built from properties`() {
        runner
            .withBean("customTelegramUrl", TelegramUrl::class.java, { TelegramUrl("http", "127.0.0.1", 4000, false) })
            .run { context ->
                assertThat(context).hasSingleBean(TelegramUrl::class.java)
                assertThat(context.getBean(TelegramUrl::class.java).port).isEqualTo(4000)
            }
    }

    private fun get(url: String): HttpResponse<String> =
        HttpClient.newHttpClient().use { client ->
            client.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
        }
}
