package io.github.pm665.telegramboot.emulator.domain

import io.github.pm665.telegramboot.features.bots.InMemoryBotsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BotIdentityResolverTest {
    private val botsService = InMemoryBotsService().apply { register("111:app", "@app_bot") }

    @Test
    fun `application bots take precedence over statically configured bots`() {
        val resolver = BotIdentityResolver({ botsService }, mapOf("111:app" to "static_bot", "222:static" to "@static_bot"))

        assertThat(resolver.username("111:app")).isEqualTo("app_bot")
        assertThat(resolver.username("222:static")).isEqualTo("static_bot")
        assertThat(resolver.username("333:unknown")).isNull()
    }

    @Test
    fun `bots enabled at runtime are resolved without restart`() {
        val resolver = BotIdentityResolver({ botsService })
        assertThat(resolver.username("444:later")).isNull()

        botsService.register("444:later", "later_bot")

        assertThat(resolver.username("444:later")).isEqualTo("later_bot")
    }

    @Test
    fun `static bots work without a BotsService`() {
        assertThat(BotIdentityResolver({ null }, mapOf("222:static" to "static_bot")).username("222:static")).isEqualTo("static_bot")
    }
}
