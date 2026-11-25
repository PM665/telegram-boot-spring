package io.github.pm665.telegramboot.emulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UiTest {
    private val fixture = EmulatorFixture()
    private val http = HttpClient.newHttpClient()

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `ui page and its assets are served from the emulator root`() {
        val page = get("/")
        assertThat(page.statusCode()).isEqualTo(200)
        assertThat(page.headers().firstValue("Content-Type")).hasValue("text/html; charset=utf-8")
        assertThat(page.headers().firstValue("Cache-Control")).hasValue("no-store")
        assertThat(page.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff")
        assertThat(page.body()).contains("<title>Telegram emulator</title>", "src=\"app.js\"", "href=\"app.css\"")
        assertThat(get("/index.html").body()).isEqualTo(page.body())

        val script = get("/app.js")
        assertThat(script.headers().firstValue("Content-Type")).hasValue("text/javascript; charset=utf-8")
        assertThat(script.body()).contains("/api/bots")
        assertThat(get("/app.css").headers().firstValue("Content-Type")).hasValue("text/css; charset=utf-8")
    }

    @Test
    fun `ui is read only and other paths are not served`() {
        val post = http.send(HttpRequest.newBuilder(uri("/")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString())
        assertThat(post.statusCode()).isEqualTo(405)
        assertThat(get("/app.min.js").statusCode()).isEqualTo(404)
        assertThat(get("/../build.gradle.kts").statusCode()).isEqualTo(404)
    }

    @Test
    fun `ui files are not exposed as static resources of a host web application`() {
        assertThat(javaClass.classLoader.getResource("META-INF/resources/index.html")).isNull()
        assertThat(javaClass.classLoader.getResource("static/index.html")).isNull()
        assertThat(javaClass.classLoader.getResource("io/github/pm665/telegramboot/emulator/ui/index.html")).isNotNull()
    }

    private fun get(path: String) = http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun uri(path: String) = URI.create("${fixture.server.baseUrl}$path")
}
