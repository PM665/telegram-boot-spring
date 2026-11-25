package io.github.pm665.telegramboot.emulator.server

import com.sun.net.httpserver.HttpHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TelegramEmulatorServerTest {
    private val noContent = HttpHandler { it.use { exchange -> exchange.sendResponseHeaders(204, -1) } }

    @Test
    fun `wildcard bind address is exposed to bots as loopback`() {
        val server = TelegramEmulatorServer("0.0.0.0", 0, noContent)
        server.start()
        try {
            assertThat(server.clientHost).isEqualTo("127.0.0.1")
            assertThat(server.baseUrl).isEqualTo("http://127.0.0.1:${server.port}")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `start and stop are idempotent`() {
        val server = TelegramEmulatorServer("127.0.0.1", 0, noContent)
        server.start()
        val port = server.port
        server.start()
        assertThat(server.port).isEqualTo(port)
        server.stop()
        server.stop()
    }
}
