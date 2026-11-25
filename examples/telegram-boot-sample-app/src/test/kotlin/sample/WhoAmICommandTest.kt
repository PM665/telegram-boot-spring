package sample

import io.github.pm665.telegramboot.domain.BotActionContext
import io.github.pm665.telegramboot.domain.ChatUtils
import io.github.pm665.telegramboot.domain.bot.BotUpdate
import io.github.pm665.telegramboot.ports.UsersService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WhoAmICommandTest {
    private val payload = ChatUtils.createPayloadData(BotUpdate())

    @Test
    fun `describes the user, the chat and the bot`() {
        val user = UsersService.User(4242, "test_user", "en")
        val chat = UsersService.Chat(4242, "sample_bot", 4242, true)
        val context = BotActionContext("sample_bot", payload, UsersService.UserChat(user, chat))

        assertEquals(
            "User: @test\\_user\nUser id: 4242\nLanguage: en\nChat id: 4242\nBot: @sample\\_bot",
            WhoAmICommand().whoAmI(context).messagesToSend.single().text,
        )
    }

    @Test
    fun `handles a user without username and language`() {
        val user = UsersService.User(7, null, "")
        val chat = UsersService.Chat(7, "bot", 7, true)
        val text = WhoAmICommand().whoAmI(BotActionContext("bot", payload, UsersService.UserChat(user, chat))).messagesToSend.single().text

        assertEquals("User: no username\nUser id: 7\nLanguage: unknown\nChat id: 7\nBot: @bot", text)
        assertEquals("I don't know you yet", WhoAmICommand().whoAmI(BotActionContext("bot", payload, null)).messagesToSend.single().text)
    }
}
