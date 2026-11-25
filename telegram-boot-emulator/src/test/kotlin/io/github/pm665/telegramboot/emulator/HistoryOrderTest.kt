package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.alice
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.bob
import io.github.pm665.telegramboot.emulator.domain.HistoryAction
import io.github.pm665.telegramboot.emulator.domain.Party
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class HistoryOrderTest {
    private val fixture = EmulatorFixture()

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `history keeps user and bot entries in the order they were received`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        val menu =
            alpha.execute(
                SendMessage
                    .builder()
                    .chatId(alice.id)
                    .text("Menu")
                    .replyMarkup(InlineKeyboardMarkup(listOf(InlineKeyboardRow(InlineKeyboardButton.builder().text("Hello").callbackData("/hello").build()))))
                    .build(),
            )
        fixture.emulator.sendMessage(ALPHA, alice.id, alice, "ping")
        val callbackQueryId = fixture.emulator.pressButton(ALPHA, alice.id, alice, menu.messageId, "/hello").callbackQueryId
        alpha.execute(AnswerCallbackQuery(callbackQueryId!!))
        alpha.execute(EditMessageText.builder().chatId(alice.id).messageId(menu.messageId).text("Menu (used)").build())
        val reply = alpha.execute(SendMessage(alice.id.toString(), "pong"))
        alpha.execute(DeleteMessage(alice.id.toString(), reply.messageId))

        val history = fixture.history(alice)
        assertThat(history.map { it.seq }).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
        assertThat(history.map { Triple(it.from, it.action, it.messageId) }).containsExactly(
            Triple(Party.USER, HistoryAction.TEXT, 1),
            Triple(Party.BOT, HistoryAction.TEXT, 2),
            Triple(Party.USER, HistoryAction.TEXT, 3),
            Triple(Party.USER, HistoryAction.CALLBACK, 2),
            Triple(Party.BOT, HistoryAction.CALLBACK_ANSWER, 2),
            Triple(Party.BOT, HistoryAction.EDIT_TEXT, 2),
            Triple(Party.BOT, HistoryAction.TEXT, 4),
            Triple(Party.BOT, HistoryAction.DELETE, 4),
        )
        assertThat(fixture.emulator.history(ALPHA, alice.id, alice.id, afterSeq = 6).map { it.seq }).containsExactly(7L, 8L)
    }

    @Test
    fun `message ids are unique within a chat and independent between chats`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        fixture.emulator.startChat(ALPHA, bob)

        val toAlice = alpha.execute(SendMessage(alice.id.toString(), "Hi Alice"))
        val toBob = alpha.execute(SendMessage(bob.id.toString(), "Hi Bob"))
        val fromAlice = fixture.emulator.sendMessage(ALPHA, alice.id, alice, "Hi bot")

        assertThat(toAlice.messageId).isEqualTo(2)
        assertThat(toBob.messageId).isEqualTo(2)
        assertThat(fromAlice.messageId).isEqualTo(3)
    }

    @Test
    fun `concurrent requests get unique message ids and history follows the arrival order`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        val requests = 40
        val start = CountDownLatch(1)

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            repeat(requests) { index ->
                executor.submit {
                    start.await()
                    if (index % 2 == 0) {
                        alpha.execute(SendMessage(alice.id.toString(), "bot $index"))
                    } else {
                        fixture.emulator.sendMessage(ALPHA, alice.id, alice, "user $index")
                    }
                }
            }
            start.countDown()
        }

        val history = fixture.history(alice)
        assertThat(history).hasSize(requests + 1)
        assertThat(history.map { it.seq }).isEqualTo((1L..requests + 1).toList())
        assertThat(history.map { it.messageId }).isEqualTo((1..requests + 1).toList())
        assertThat(history.drop(1).map { it.text }).containsExactlyInAnyOrderElementsOf(
            (0 until requests).map { if (it % 2 == 0) "bot $it" else "user $it" },
        )
    }
}
