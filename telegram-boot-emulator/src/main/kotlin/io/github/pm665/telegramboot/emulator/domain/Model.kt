package io.github.pm665.telegramboot.emulator.domain

import java.time.Instant

/** The emulated Telegram user acting in the emulator. */
data class UserProfile(
    val id: Long,
    val username: String? = null,
    val firstName: String? = null,
    val languageCode: String? = null,
) {
    init {
        if (id <= 0) throw EmulatorException.badRequest("user id must be a positive number")
    }

    val displayName: String get() = firstName?.takeIf { it.isNotBlank() } ?: username?.takeIf { it.isNotBlank() } ?: "User $id"

    /** Profile details missing in this request are kept from the [known] profile of the same user. */
    fun orElse(known: UserProfile): UserProfile =
        copy(
            username = username ?: known.username,
            firstName = firstName ?: known.firstName,
            languageCode = languageCode ?: known.languageCode,
        )
}

/** Inline keyboard button. Only buttons with [callbackData] can be pressed in the emulator. */
data class Button(
    val text: String,
    val callbackData: String? = null,
    val url: String? = null,
)

enum class Party { USER, BOT }

enum class HistoryAction(
    /** Text messages are shown as chat bubbles, everything else as a short activity line. */
    val isTextMessage: Boolean,
) {
    TEXT(true),
    RICH_MESSAGE(true),
    PHOTO(false),
    LOCATION(false),
    CALLBACK(false),
    CALLBACK_ANSWER(false),
    EDIT_TEXT(false),
    EDIT_MARKUP(false),
    DELETE(false),
    PIN(false),
    UNPIN(false),
}

/**
 * Immutable chat history record. [seq] reflects the order in which the emulator received the action,
 * [messageId] is the message created or targeted by the action.
 */
data class HistoryEntry(
    val seq: Long,
    val date: Instant,
    val from: Party,
    val action: HistoryAction,
    val messageId: Int? = null,
    val text: String? = null,
    val buttons: List<List<Button>> = emptyList(),
    val info: String? = null,
)

data class BotSummary(
    val id: Long,
    val username: String,
    val registeredAt: Instant,
    val online: Boolean,
)

data class ChatSummary(
    val id: Long,
    val owner: UserProfile,
    val startedAt: Instant,
)

/** Result of a user action, [updateId] is the update delivered to the bot. */
data class UserActionResult(
    val chatId: Long,
    val seq: Long,
    val updateId: Int,
    val messageId: Int? = null,
    val callbackQueryId: String? = null,
)

data class StartChatResult(
    val chat: ChatSummary,
    /** `/start` sent on chat creation, `null` when the chat already existed. */
    val start: UserActionResult?,
)

data class PhotoFile(
    val fileId: String,
    val fileUniqueId: String,
    val name: String?,
    val size: Int?,
)

/** Photo sent by a bot: a new upload or a `file_id` of a photo sent before. */
sealed interface PhotoInput {
    data class Upload(
        val filename: String?,
        val bytes: ByteArray,
    ) : PhotoInput {
        override fun equals(other: Any?) = other is Upload && filename == other.filename && bytes.contentEquals(other.bytes)

        override fun hashCode() = 31 * filename.hashCode() + bytes.contentHashCode()
    }

    data class FileId(
        val fileId: String,
    ) : PhotoInput
}

data class BotCommandItem(
    val command: String,
    val description: String,
)
