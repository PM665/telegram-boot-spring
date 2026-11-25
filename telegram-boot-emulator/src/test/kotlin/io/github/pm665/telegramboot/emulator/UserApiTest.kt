package io.github.pm665.telegramboot.emulator

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.BETA_TOKEN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class UserApiTest {
    private val fixture = EmulatorFixture()
    private val alpha = fixture.registeredClient(ALPHA_TOKEN)
    private val http = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private val chat = "/api/bots/$ALPHA/chats/4242"

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `registered bots are listed`() {
        fixture.registeredClient(BETA_TOKEN)

        val bots = get("/api/bots")

        assertThat(bots.status).isEqualTo(200)
        assertThat(bots.body.map { it["username"].asText() }).containsExactly("alpha_bot", "beta_bot")
        assertThat(bots.body[0]["id"].asLong()).isEqualTo(111)
        assertThat(bots.body[0]["online"].isBoolean).isTrue()
        assertThat(bots.body[0]["registeredAt"].asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z")
    }

    @Test
    fun `user starts a chat, reads the history and presses a button of the bot`() {
        val started = post("/api/bots/$ALPHA/chats", """{"userId":4242,"username":"alice","firstName":"Alice","languageCode":"en"}""")
        assertThat(started.status).isEqualTo(201)
        assertThat(started.body["created"].asBoolean()).isTrue()
        assertThat(started.body["chat"]["id"].asLong()).isEqualTo(4242)
        assertThat(started.body["chat"]["username"].asText()).isEqualTo("alice")
        assertThat(started.body["start"]["messageId"].asInt()).isEqualTo(1)

        val startUpdate = fixture.updates(alpha).single().message
        assertThat(listOf(startUpdate.text, startUpdate.from.userName, startUpdate.chat.id)).containsExactly("/start", "alice", 4242L)

        val menuButton = InlineKeyboardButton.builder().text("Hello").callbackData("/hello").build()
        alpha.execute(SendMessage.builder().chatId(4242L).text("Menu").replyMarkup(InlineKeyboardMarkup(listOf(InlineKeyboardRow(menuButton)))).build())

        val history = get("$chat/history?userId=4242")
        assertThat(history.status).isEqualTo(200)
        val menu = history.body[1]
        assertThat(menu["date"].asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z")
        assertThat(json.writeValueAsString((menu as ObjectNode).deepCopy().apply { remove("date") }))
            .isEqualTo(
                """{"seq":2,"from":"bot","action":"text","kind":"message","messageId":2,"text":"Menu",""" +
                    """"buttons":[[{"text":"Hello","callbackData":"/hello"}]]}""",
            )

        val pressed = post("$chat/callbacks", """{"userId":4242,"messageId":2,"data":"/hello"}""")
        assertThat(pressed.status).isEqualTo(200)
        val callbackQueryId = pressed.body["callbackQueryId"].asText()
        alpha.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).text("Hi!").build())

        val recent = get("$chat/history?userId=4242&after=2")
        assertThat(recent.body.map { Triple(it["from"].asText(), it["action"].asText(), it["kind"].asText()) }).containsExactly(
            Triple("user", "callback", "activity"),
            Triple("bot", "callback_answer", "activity"),
        )
        assertThat(recent.body[0]["info"].asText()).isEqualTo("/hello")
        assertThat(recent.body[1]["text"].asText()).isEqualTo("Hi!")
    }

    @Test
    fun `message identifies the user by id and keeps the known profile`() {
        post("/api/bots/$ALPHA/chats", """{"userId":4242,"username":"alice"}""")

        val sent = post("$chat/messages", """{"userId":4242,"text":"/hello"}""")

        assertThat(sent.status).isEqualTo(200)
        assertThat(sent.body["messageId"].asInt()).isEqualTo(2)
        assertThat(sent.body["updateId"].asInt()).isEqualTo(2)
        val hello = fixture.updates(alpha).last().message
        assertThat(listOf(hello.text, hello.from.id, hello.from.userName)).containsExactly("/hello", 4242L, "alice")
        assertThat(get("/api/bots/$ALPHA/chats?userId=4242").body.single()["username"].asText()).isEqualTo("alice")
        assertThat(get("/api/bots/$ALPHA/chats?userId=4343").body).isEmpty()
    }

    @Test
    fun `starting a started chat is idempotent`() {
        post("/api/bots/$ALPHA/chats", """{"userId":4242}""")

        val again = post("/api/bots/$ALPHA/chats", """{"userId":4242}""")

        assertThat(again.status).isEqualTo(200)
        assertThat(again.body["created"].asBoolean()).isFalse()
        assertThat(again.body.has("start")).isFalse()
    }

    @Test
    fun `only the user who started the chat acts in it`() {
        post("/api/bots/$ALPHA/chats", """{"userId":4242}""")

        listOf(
            post("$chat/messages", """{"userId":4343,"text":"hi"}"""),
            post("$chat/callbacks", """{"userId":4343,"messageId":1,"data":"/start"}"""),
            get("$chat/history?userId=4343"),
        ).forEach { response ->
            assertThat(response.status).isEqualTo(403)
            assertThat(json.writeValueAsString(response.body))
                .isEqualTo("""{"status":403,"message":"Forbidden: chat 4242 belongs to another user"}""")
        }
    }

    @Test
    fun `chat must be started and the bot registered`() {
        assertThat(post("$chat/messages", """{"userId":4242,"text":"hi"}""").status).isEqualTo(404)
        assertThat(get("$chat/history?userId=4242").status).isEqualTo(404)
        assertThat(post("/api/bots/unknown_bot/chats", """{"userId":4242}""").status).isEqualTo(404)
    }

    @Test
    fun `invalid requests are rejected with a reason`() {
        post("/api/bots/$ALPHA/chats", """{"userId":4242}""")

        fun rejected(
            response: Response,
            status: Int,
            message: String,
        ) {
            assertThat(response.status).isEqualTo(status)
            assertThat(response.body["message"].asText()).isEqualTo(message)
        }
        rejected(post("/api/bots/$ALPHA/chats", """{"username":"alice"}"""), 400, "Bad Request: userId is required")
        rejected(post("/api/bots/$ALPHA/chats", """{"userId":-1}"""), 400, "Bad Request: user id must be a positive number")
        rejected(post("/api/bots/$ALPHA/chats", "not json"), 400, "Bad Request: request body must be JSON")
        rejected(post("$chat/messages", """{"userId":4242,"text":"  "}"""), 400, "Bad Request: message text is empty")
        rejected(post("$chat/callbacks", """{"userId":4242,"data":"/x"}"""), 400, "Bad Request: messageId is required")
        rejected(get("$chat/history?userId=4242&after=x"), 400, "Bad Request: after must be a number")
        rejected(get("/api/bots/$ALPHA/chats/abc/history?userId=4242"), 400, "Bad Request: chat id must be a number")
        rejected(get("$chat/messages"), 405, "Method Not Allowed: GET /api/bots/alpha_bot/chats/4242/messages")
        rejected(get("/api/unknown"), 404, "Not Found: /api/unknown")
    }

    private fun get(path: String) = send(HttpRequest.newBuilder(uri(path)).GET())

    private fun post(
        path: String,
        body: String,
    ) = send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)))

    private fun send(request: HttpRequest.Builder): Response {
        val response = http.send(request.build(), HttpResponse.BodyHandlers.ofString())
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json; charset=utf-8")
        return Response(response.statusCode(), json.readTree(response.body()))
    }

    private fun uri(path: String) = URI.create("${fixture.server.baseUrl}$path")

    private data class Response(
        val status: Int,
        val body: JsonNode,
    )
}
