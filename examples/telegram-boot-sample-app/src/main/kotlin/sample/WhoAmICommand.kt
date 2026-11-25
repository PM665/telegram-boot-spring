package sample

import io.github.pm665.telegramboot.domain.AccessType
import io.github.pm665.telegramboot.domain.BotActionContext
import io.github.pm665.telegramboot.domain.actions.ActionResult
import io.github.pm665.telegramboot.domain.actions.SendMessageMarkup
import io.github.pm665.telegramboot.domain.annotations.BotAction
import io.github.pm665.telegramboot.domain.annotations.BotCommand

/** `/whoami`: what the library knows about the user, the chat and the bot that serves it. */
@BotCommand(name = "whoami", displayName = "Who am I", accessType = AccessType.ALL, keepMessage = true)
class WhoAmICommand {
    @BotAction
    fun whoAmI(context: BotActionContext): ActionResult {
        val userChat = context.user
        val text =
            if (userChat == null) {
                "I don't know you yet"
            } else {
                val user = userChat.user
                listOf(
                    "User: ${user.username?.takeIf { it.isNotBlank() }?.let { "@${it.escapeMarkdown()}" } ?: "no username"}",
                    "User id: ${user.userId}",
                    "Language: ${user.locale.ifBlank { "unknown" }.escapeMarkdown()}",
                    "Chat id: ${userChat.chat.chatId}",
                    "Bot: @${context.botUsername.escapeMarkdown()}",
                ).joinToString("\n")
            }
        return ActionResult.builder().messagesToSend(listOf(SendMessageMarkup(text))).build()
    }
}
