package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.alice
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.apiError
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.userError
import io.github.pm665.telegramboot.emulator.domain.Button
import io.github.pm665.telegramboot.emulator.domain.HistoryAction
import io.github.pm665.telegramboot.features.messaging.InputRichMessage
import io.github.pm665.telegramboot.features.messaging.SendRichMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.commands.GetMyCommands
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.UnpinChatMessage
import org.telegram.telegrambots.meta.api.methods.send.SendLocation
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class BotApiMethodsTest {
    private val fixture = EmulatorFixture()
    private val alpha = fixture.registeredClient(ALPHA_TOKEN)
    private val chatId = alice.id.toString()

    @BeforeEach
    fun startChat() {
        fixture.emulator.startChat(ALPHA, alice)
    }

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `rich message of the library is shown as text with callback buttons`() {
        val html =
            "<p>Commands &amp; more</p><tg-button-row align=\"left\">" +
                "<tg-button type=\"callback_data\" style=\"primary\" data=\"/hello\">Hello</tg-button>" +
                "<tg-button type=\"callback_data\" style=\"primary\" data=\"/a&quot;b\">Say &lt;hi&gt;</tg-button>" +
                "</tg-button-row>"

        val message = alpha.execute(SendRichMessage(chatId, InputRichMessage(html)))

        val entry = fixture.history(alice).last()
        assertThat(entry.action).isEqualTo(HistoryAction.RICH_MESSAGE)
        assertThat(entry.messageId).isEqualTo(message.messageId)
        assertThat(entry.text).isEqualTo("Commands & more")
        assertThat(entry.buttons).containsExactly(listOf(Button("Hello", "/hello"), Button("Say <hi>", "/a\"b")))
        assertThat(fixture.emulator.pressButton(ALPHA, alice.id, alice, message.messageId, "/hello").callbackQueryId).isNotNull()
    }

    @Test
    fun `plain message keeps text, parse mode and inline keyboard, reply keyboards are ignored`() {
        val markdown = SendMessage(chatId, "Hello @test\\_user").apply { enableMarkdown(true) }
        markdown.replyMarkup = ReplyKeyboardRemove(true)

        val message = alpha.execute(markdown)

        assertThat(message.text).isEqualTo("Hello @test\\_user")
        val entry = fixture.history(alice).last()
        assertThat(listOf(entry.action, entry.text, entry.info)).containsExactly(HistoryAction.TEXT, "Hello @test\\_user", "parse mode Markdown")
        assertThat(entry.buttons).isEmpty()
    }

    @Test
    fun `uploaded photo gets a file id that can be sent again, unknown file ids are rejected`() {
        val uploaded =
            alpha.execute(
                SendPhoto
                    .builder()
                    .chatId(chatId)
                    .photo(InputFile(ByteArrayInputStream(ByteArray(1234)), "cat.jpg"))
                    .caption("A cat")
                    .replyMarkup(keyboard("Like" to "/like"))
                    .parseMode("Markdown")
                    .build(),
            )
        val fileId = uploaded.photo.single().fileId

        val resent = alpha.execute(SendPhoto(chatId, InputFile(fileId)))

        assertThat(resent.photo.single().fileId).isEqualTo(fileId)
        val (first, second) = fixture.history(alice).takeLast(2)
        assertThat(listOf(first.action, first.text, first.info)).containsExactly(HistoryAction.PHOTO, "A cat", "cat.jpg, 1234 bytes")
        assertThat(first.buttons).containsExactly(listOf(Button("Like", "/like")))
        assertThat(second.info).isEqualTo("cat.jpg, 1234 bytes")
        assertThat(apiError { alpha.execute(SendPhoto(chatId, InputFile("unknown-file"))) }.errorCode).isEqualTo(400)
    }

    @Test
    fun `location is recorded with its coordinates`() {
        val message = alpha.execute(SendLocation(chatId, 52.52, 13.405))

        assertThat(message.location.latitude).isEqualTo(52.52)
        val entry = fixture.history(alice).last()
        assertThat(listOf(entry.action, entry.info)).containsExactly(HistoryAction.LOCATION, "52.52, 13.405")
    }

    @Test
    fun `edits change the buttons that can be pressed`() {
        val menu = alpha.execute(SendMessage.builder().chatId(chatId).text("Menu").replyMarkup(keyboard("One" to "/one")).build())

        alpha.execute(EditMessageReplyMarkup.builder().chatId(chatId).messageId(menu.messageId).replyMarkup(keyboard("Two" to "/two")).build())
        assertThat(userError { fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/one") }.status).isEqualTo(400)
        fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/two")

        val edited = alpha.execute(EditMessageText.builder().chatId(chatId).messageId(menu.messageId).text("Done").build())
        assertThat((edited as Message).text).isEqualTo("Done")
        assertThat(userError { fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/two") }.status).isEqualTo(400)
        assertThat(fixture.history(alice).map { it.action }).containsSequence(
            HistoryAction.EDIT_MARKUP,
            HistoryAction.CALLBACK,
            HistoryAction.EDIT_TEXT,
        )
    }

    @Test
    fun `bot edits only its own existing messages`() {
        assertThat(apiError { alpha.execute(EditMessageText.builder().chatId(chatId).messageId(1).text("x").build()) }.apiResponse)
            .isEqualTo("Bad Request: message can't be edited")
        assertThat(apiError { alpha.execute(EditMessageText.builder().chatId(chatId).messageId(99).text("x").build()) }.apiResponse)
            .isEqualTo("Bad Request: message to edit not found")
    }

    @Test
    fun `deleted message cannot be pressed, edited or deleted again`() {
        val menu = alpha.execute(SendMessage.builder().chatId(chatId).text("Menu").replyMarkup(keyboard("One" to "/one")).build())

        assertThat(alpha.execute(DeleteMessage(chatId, menu.messageId))).isTrue()

        assertThat(userError { fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/one") }.status).isEqualTo(400)
        assertThat(apiError { alpha.execute(DeleteMessage(chatId, menu.messageId)) }.apiResponse).isEqualTo("Bad Request: message to delete not found")
        assertThat(apiError { alpha.execute(EditMessageText.builder().chatId(chatId).messageId(menu.messageId).text("x").build()) }.errorCode)
            .isEqualTo(400)
    }

    @Test
    fun `user messages have no buttons to press and can be deleted by the bot`() {
        assertThat(userError { fixture.emulator.pressButton(ALPHA, alice.id, alice, 1, "/start") }.status).isEqualTo(400)
        assertThat(alpha.execute(DeleteMessage(chatId, 1))).isTrue()
    }

    @Test
    fun `pin and unpin are recorded`() {
        val message = alpha.execute(SendMessage(chatId, "Important"))

        alpha.execute(PinChatMessage(chatId, message.messageId))
        alpha.execute(UnpinChatMessage.builder().chatId(chatId).messageId(message.messageId).build())

        assertThat(fixture.history(alice).takeLast(2).map { it.action to it.messageId })
            .containsExactly(HistoryAction.PIN to message.messageId, HistoryAction.UNPIN to message.messageId)
        assertThat(apiError { alpha.execute(PinChatMessage(chatId, 99)) }.errorCode).isEqualTo(400)
    }

    @Test
    fun `commands are stored per language`() {
        alpha.execute(SetMyCommands.builder().command(BotCommand("start", "Start")).scope(BotCommandScopeDefault()).build())
        alpha.execute(SetMyCommands.builder().command(BotCommand("start", "Starten")).languageCode("de").build())

        assertThat(alpha.execute(GetMyCommands()).map { it.command to it.description }).containsExactly("start" to "Start")
        assertThat(alpha.execute(GetMyCommands.builder().languageCode("de").build()).single().description).isEqualTo("Starten")
    }

    @Test
    fun `unsupported methods and paths are reported as not found`() {
        val client = HttpClient.newHttpClient()
        fun post(path: String) =
            client.send(
                HttpRequest.newBuilder(URI.create("${fixture.server.baseUrl}$path")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        val method = post("/bot$ALPHA_TOKEN/sendDice")
        assertThat(method.statusCode()).isEqualTo(404)
        assertThat(method.body()).isEqualTo("""{"ok":false,"error_code":404,"description":"Not Found: method senddice is not supported by the Telegram emulator"}""")
        assertThat(post("/unknown").statusCode()).isEqualTo(404)
    }

    @Test
    fun `parameters are also accepted in the query string`() {
        val client = HttpClient.newHttpClient()
        val response =
            client.send(
                HttpRequest.newBuilder(URI.create("${fixture.server.baseUrl}/bot$ALPHA_TOKEN/sendMessage?chat_id=$chatId&text=Hi%20there")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(fixture.history(alice).last().text).isEqualTo("Hi there")
    }

    private fun keyboard(vararg buttons: Pair<String, String>) =
        InlineKeyboardMarkup(
            listOf(InlineKeyboardRow(buttons.map { (text, data) -> InlineKeyboardButton.builder().text(text).callbackData(data).build() })),
        )
}
