package sample

import io.github.pm665.telegramboot.domain.AccessType
import io.github.pm665.telegramboot.domain.BotActionContext
import io.github.pm665.telegramboot.domain.actions.ActionResult
import io.github.pm665.telegramboot.domain.actions.SendMessageMarkup
import io.github.pm665.telegramboot.domain.annotations.BotAction
import io.github.pm665.telegramboot.domain.annotations.BotCommand

/** A minimal command: `/hello`, also shown as a button in the `/start` menu. */
@BotCommand(name = "hello", accessType = AccessType.ALL, keepMessage = true)
class HelloCommand {
    @BotAction
    fun hello(context: BotActionContext): ActionResult {
        val username = context.user?.user?.username
        val greeting = username?.takeIf { it.isNotBlank() }?.let { "@${it.escapeMarkdown()}" } ?: "there"
        return ActionResult.builder().messagesToSend(listOf(SendMessageMarkup("Hello $greeting"))).build()
    }
}
