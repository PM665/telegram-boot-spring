package io.github.pm665.telegramboot.emulator

import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.ALPHA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.BETA
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.BETA_TOKEN
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.alice
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.apiError
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.bob
import io.github.pm665.telegramboot.emulator.EmulatorFixture.Companion.userError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.telegram.telegrambots.meta.api.methods.send.SendLocation
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow

/** Only a user starts a chat, and only the user who started it acts in it. */
class ChatOwnershipTest {
    private val fixture = EmulatorFixture()

    @AfterEach
    fun close() = fixture.close()

    @Test
    fun `bot cannot start a chat with a user`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)

        val error = apiError { alpha.execute(SendMessage(alice.id.toString(), "Hi")) }
        assertThat(error.errorCode).isEqualTo(403)
        assertThat(error.apiResponse).isEqualTo("Forbidden: bot can't initiate conversation with a user")
        assertThat(apiError { alpha.execute(SendLocation(alice.id.toString(), 52.5, 13.4)) }.errorCode).isEqualTo(403)
        assertThat(fixture.emulator.chats(ALPHA, alice.id)).isEmpty()
    }

    @Test
    fun `chat started with one bot does not allow another bot to write to the user`() {
        val beta = fixture.registeredClient(BETA_TOKEN)
        fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)

        assertThat(apiError { beta.execute(SendMessage(alice.id.toString(), "Hi")) }.errorCode).isEqualTo(403)
        assertThat(fixture.emulator.chats(BETA, alice.id)).isEmpty()
        assertThat(fixture.history(alice).map { it.text }).containsExactly("/start")
    }

    @Test
    fun `another user cannot act in the chat`() {
        val alpha = fixture.registeredClient(ALPHA_TOKEN)
        fixture.emulator.startChat(ALPHA, alice)
        val menu =
            alpha.execute(
                SendMessage
                    .builder()
                    .chatId(alice.id)
                    .text("Menu")
                    .replyMarkup(InlineKeyboardMarkup(listOf(InlineKeyboardRow(InlineKeyboardButton.builder().text("Hi").callbackData("/hi").build()))))
                    .build(),
            )

        listOf(
            userError { fixture.emulator.sendMessage(ALPHA, alice.id, bob, "/hello") },
            userError { fixture.emulator.pressButton(ALPHA, alice.id, bob, menu.messageId, "/hi") },
            userError { fixture.emulator.history(ALPHA, alice.id, bob.id) },
        ).forEach { error ->
            assertThat(error.status).isEqualTo(403)
            assertThat(error.description).isEqualTo("Forbidden: chat ${alice.id} belongs to another user")
        }
        assertThat(fixture.history(alice).map { it.text }).containsExactly("/start", "Menu")
    }

    @Test
    fun `user acts only in a started chat`() {
        fixture.registeredClient(ALPHA_TOKEN)

        val error = userError { fixture.emulator.sendMessage(ALPHA, alice.id, alice, "/hello") }
        assertThat(error.status).isEqualTo(404)
        assertThat(userError { fixture.emulator.history(ALPHA, alice.id, alice.id) }.status).isEqualTo(404)
        assertThat(userError { fixture.emulator.startChat("unknown_bot", alice) }.status).isEqualTo(404)
    }

    @Test
    fun `starting a started chat keeps it and does not send start again`() {
        fixture.registeredClient(ALPHA_TOKEN)

        val first = fixture.emulator.startChat(ALPHA, alice)
        val second = fixture.emulator.startChat(ALPHA, alice.copy(username = "alice_renamed"))

        assertThat(first.start).isNotNull()
        assertThat(second.start).isNull()
        assertThat(second.chat.owner.username).isEqualTo("alice_renamed")
        assertThat(fixture.history(alice).map { it.text }).containsExactly("/start")
    }
}
