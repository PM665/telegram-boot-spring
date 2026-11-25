import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import sample.SampleApplication
import java.time.Duration
import java.time.Instant

/** The sample application with its `emulator` profile: bots talk to the embedded Telegram emulator. */
@SpringBootTest(classes = [SampleApplication::class], properties = ["telegram-boot.emulator.port=0"])
@ActiveProfiles("emulator")
class TelegramBotE2eTest {
    @Value("\${local.telegram-boot.emulator.port}")
    private var port: Int = 0

    private fun user(
        id: Long,
        username: String? = null,
        languageCode: String? = null,
    ) = EmulatorUser("http://127.0.0.1:$port", id, username, languageCode)

    @Test
    fun `all bots of the application are registered and polling`() {
        val user = user(4240)
        val deadline = Instant.now().plus(Duration.ofSeconds(10))
        while (user.bots().count { it["online"] == true } < 2 && Instant.now().isBefore(deadline)) Thread.sleep(100)

        assertThat(user.bots().map { it["username"] to it["online"] })
            .containsExactly(SAMPLE_BOT to true, SECOND_BOT to true)
    }

    @Test
    fun `start sends the command menu and a menu button invokes the command`() {
        val user = user(4244, "startuser")

        assertThat(user.startChat(SAMPLE_BOT).statusCode()).isEqualTo(201)

        val menu = user.awaitEntry(SAMPLE_BOT) { it["from"] == "bot" && it["action"] == "rich_message" }

        @Suppress("UNCHECKED_CAST")
        val buttons = (menu["buttons"] as List<List<Map<String, Any?>>>).flatten().map { it["text"] to it["callbackData"] }
        assertThat(buttons).contains("hello" to "/hello", "Who am I" to "/whoami")

        assertThat(user.press(SAMPLE_BOT, menu["messageId"] as Int, "/hello").statusCode()).isEqualTo(200)

        val reply = user.awaitEntry(SAMPLE_BOT) { it["from"] == "bot" && it["text"] == "Hello @startuser" }
        val pressed = user.history(SAMPLE_BOT).single { it["action"] == "callback" }
        assertThat(pressed["info"]).isEqualTo("/hello")
        assertThat(pressed["seq"] as Int).isLessThan(reply["seq"] as Int)
    }

    @Test
    fun `hello command returns a greeting`() {
        val user = user(4242, "alice")
        user.startChat(SAMPLE_BOT)

        user.send(SAMPLE_BOT, "/hello")

        user.awaitEntry(SAMPLE_BOT) { it["from"] == "bot" && it["text"] == "Hello @alice" }
    }

    @Test
    fun `hello command sends an escaped username in Markdown`() {
        val user = user(4243, "test_user")
        user.startChat(SAMPLE_BOT)

        user.send(SAMPLE_BOT, "/hello")

        val reply = user.awaitEntry(SAMPLE_BOT) { it["from"] == "bot" && it["action"] == "text" }
        assertThat(reply["text"]).isEqualTo("Hello @test\\_user")
        assertThat(reply["info"]).isEqualTo("parse mode Markdown")
    }

    @Test
    fun `whoami shows the user info known to the bot that serves the chat`() {
        val user = user(4245, "bob", "de")
        user.startChat(SECOND_BOT)

        user.send(SECOND_BOT, "/whoami")

        val reply = user.awaitEntry(SECOND_BOT) { it["from"] == "bot" && it["action"] == "text" }
        assertThat(reply["text"]).isEqualTo("User: @bob\nUser id: 4245\nLanguage: de\nChat id: 4245\nBot: @second\\_sample\\_bot")
        assertThat(user.historyResponse(SAMPLE_BOT).statusCode()).isEqualTo(404)
    }

    private companion object {
        const val SAMPLE_BOT = "sample_bot"
        const val SECOND_BOT = "second_sample_bot"
    }
}
