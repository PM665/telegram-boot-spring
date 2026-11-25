package io.github.pm665.telegramboot.emulator.userapi

import io.github.pm665.telegramboot.emulator.domain.BotSummary
import io.github.pm665.telegramboot.emulator.domain.Button
import io.github.pm665.telegramboot.emulator.domain.ChatSummary
import io.github.pm665.telegramboot.emulator.domain.HistoryEntry
import io.github.pm665.telegramboot.emulator.domain.UserActionResult
import java.time.Instant

/** JSON contract of the user API. */
internal data class BotResponse(
    val id: Long,
    val username: String,
    val online: Boolean,
    val registeredAt: Instant,
) {
    constructor(bot: BotSummary) : this(bot.id, bot.username, bot.online, bot.registeredAt)
}

internal data class ChatResponse(
    val id: Long,
    val userId: Long,
    val username: String?,
    val firstName: String?,
    val languageCode: String?,
    val startedAt: Instant,
) {
    constructor(chat: ChatSummary) : this(
        chat.id,
        chat.owner.id,
        chat.owner.username,
        chat.owner.firstName,
        chat.owner.languageCode,
        chat.startedAt,
    )
}

internal data class StartChatResponse(
    val created: Boolean,
    val chat: ChatResponse,
    /** `/start` sent when the chat was created. */
    val start: UserActionResult?,
)

/**
 * History entry. `kind` tells how to show it: `message` entries are chat bubbles with text and buttons,
 * `activity` entries are a short line with the `action` name and `info`.
 */
internal data class EntryResponse(
    val seq: Long,
    val date: Instant,
    val from: String,
    val action: String,
    val kind: String,
    val messageId: Int?,
    val text: String?,
    val buttons: List<List<Button>>,
    val info: String?,
) {
    constructor(entry: HistoryEntry) : this(
        entry.seq,
        entry.date,
        entry.from.name.lowercase(),
        entry.action.name.lowercase(),
        if (entry.action.isTextMessage) "message" else "activity",
        entry.messageId,
        entry.text,
        entry.buttons,
        entry.info,
    )
}

internal data class ErrorResponse(
    val status: Int,
    val message: String,
)
