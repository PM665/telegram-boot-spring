package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.BETA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.BETA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.alice
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.apiError
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.bob
import io.github.pm665.telegramboot.emulator.domain.HistoryAction
import io.github.pm665.telegramboot.emulator.domain.Party
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.GetMe
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow

class IdentificationTest {
    private val fixture = EmulatorFixture()

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `getMe registers the bot identified by its token`() {
        val alpha = fixture.client(ALPHA_TOKEN).execute(GetMe())
        val beta = fixture.client(BETA_TOKEN).execute(GetMe())

        assertThat(listOf(alpha.id, alpha.userName, alpha.isBot)).containsExactly(111L, ALPHA, true)
        assertThat(listOf(beta.id, beta.userName, beta.isBot)).containsExactly(222L, BETA, true)
        assertThat(fixture.emulator.bots().map { it.username }).containsExactly(ALPHA, BETA)
    }

    @Test
    fun `unknown token is unauthorized and a bot must call getMe before other methods`() {
        val unknown = apiError { fixture.client("333:unknown").execute(GetMe()) }
        assertThat(unknown.errorCode).isEqualTo(401)
        assertThat(fixture.emulator.bots()).isEmpty()

        val unregistered = apiError { fixture.updates(fixture.client(ALPHA_TOKEN)) }
        assertThat(unregistered.errorCode).isEqualTo(401)
        assertThat(unregistered.apiResponse).isEqualTo("Unauthorized: bot is not registered in the emulator, getMe must be called first")
    }

    @Test
    fun `user message is delivered only to the bot of the chat with the user and chat identity`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        val beta = fixture.registeredClient(BETA_TOKEN)

        fixture.emulator.startChat(ALPHA, alice)
        fixture.emulator.sendMessage(ALPHA, alice.id, alice, "/hello world")

        assertThat(fixture.updates(beta)).isEmpty()
        val messages = fixture.updates(alpha).map { it.message }
        assertThat(messages.map { it.text }).containsExactly("/start", "/hello world")
        val hello = messages.last()
        assertThat(listOf(hello.from.id, hello.from.userName, hello.from.firstName, hello.from.languageCode, hello.from.isBot))
            .containsExactly(alice.id, "alice", "Alice", "en", false)
        assertThat(listOf(hello.chat.id, hello.chat.type, hello.chat.userName)).containsExactly(alice.id, "private", "alice")
        val command = hello.entities.single()
        assertThat(listOf(command.type, command.offset, command.length)).containsExactly("bot_command", 0, "/hello".length)
    }

    @Test
    fun `chats of different users with the same bot are separate`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        fixture.emulator.startChat(ALPHA, bob)

        alpha.execute(SendMessage(bob.id.toString(), "Hi Bob"))

        assertThat(fixture.history(bob).map { it.from to it.text }).containsExactly(Party.USER to "/start", Party.BOT to "Hi Bob")
        assertThat(fixture.history(alice).map { it.text }).containsExactly("/start")
        assertThat(fixture.updates(alpha).map { it.message.from.id to it.message.chat.id })
            .containsExactly(alice.id to alice.id, bob.id to bob.id)
    }

    @Test
    fun `callback query identifies the user, the chat and the pressed message`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        val menu = alpha.execute(SendMessage.builder().chatId(alice.id).text("Menu").replyMarkup(keyboard("Hello" to "/hello")).build())
        val offset = fixture.updates(alpha).last().updateId + 1

        val press = fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/hello")

        val callback = fixture.updates(alpha, offset).single().callbackQuery
        assertThat(callback.id).isEqualTo(press.callbackQueryId)
        assertThat(callback.data).isEqualTo("/hello")
        assertThat(callback.from.id).isEqualTo(alice.id)
        assertThat(callback.message.chat.id).isEqualTo(alice.id)
        assertThat(callback.message.chat.type).isEqualTo("private")
        assertThat(callback.message.messageId).isEqualTo(menu.messageId)
        assertThat(callback.message.date).isPositive()

        alpha.execute(AnswerCallbackQuery.builder().callbackQueryId(callback.id).text("Done").showAlert(true).build())
        val answer = fixture.history(alice).last()
        assertThat(listOf(answer.from, answer.action, answer.messageId, answer.text, answer.info))
            .containsExactly(Party.BOT, HistoryAction.CALLBACK_ANSWER, menu.messageId, "Done", "alert")
    }

    @Test
    fun `callback query can be answered only once and only by the bot it was sent to`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        val beta = fixture.registeredClient(BETA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        val menu = alpha.execute(SendMessage.builder().chatId(alice.id).text("Menu").replyMarkup(keyboard("Hello" to "/hello")).build())
        val callbackQueryId = fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/hello").callbackQueryId!!
        val answer = AnswerCallbackQuery(callbackQueryId)

        assertThat(apiError { beta.execute(answer) }.errorCode).isEqualTo(400)
        assertThat(alpha.execute(answer)).isTrue()
        assertThat(apiError { alpha.execute(answer) }.errorCode).isEqualTo(400)
    }

    private fun keyboard(vararg buttons: Pair<String, String>) =
        InlineKeyboardMarkup(
            listOf(InlineKeyboardRow(buttons.map { (text, data) -> InlineKeyboardButton.builder().text(text).callbackData(data).build() })),
        )
}
