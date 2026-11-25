package io.github.pm665.telegramboot.emulator.domain

import java.time.Instant

/** Current state of a chat message, used to validate bot edits and user button presses. */
internal class ChatMessage(
    val messageId: Int,
    val from: Party,
    val action: HistoryAction,
    val date: Instant,
    var text: String?,
    var buttons: List<List<Button>>,
    val photo: PhotoFile? = null,
    val location: Location? = null,
) {
    var deleted: Boolean = false

    fun button(callbackData: String): Button? = buttons.flatten().firstOrNull { it.callbackData == callbackData }

    data class Location(
        val latitude: Double,
        val longitude: Double,
    )
}

/**
 * Private chat between a bot and the user who started it. The chat id is the user id, as in Telegram.
 * Message ids are unique within the chat and shared by the user and the bot.
 * Not thread safe: guarded by the owning [EmulatedBot].
 */
internal class EmulatedChat(
    var owner: UserProfile,
    val startedAt: Instant,
) {
    val id: Long = owner.id
    private var lastMessageId = 0
    private var lastSeq = 0L
    private val entries = mutableListOf<HistoryEntry>()
    private val messages = HashMap<Int, ChatMessage>()

    fun addMessage(
        from: Party,
        action: HistoryAction,
        date: Instant,
        text: String?,
        buttons: List<List<Button>> = emptyList(),
        photo: PhotoFile? = null,
        location: ChatMessage.Location? = null,
    ): ChatMessage = ChatMessage(++lastMessageId, from, action, date, text, buttons, photo, location).also { messages[it.messageId] = it }

    fun message(messageId: Int): ChatMessage? = messages[messageId]?.takeUnless { it.deleted }

    fun record(
        from: Party,
        action: HistoryAction,
        date: Instant,
        messageId: Int? = null,
        text: String? = null,
        buttons: List<List<Button>> = emptyList(),
        info: String? = null,
    ): HistoryEntry = HistoryEntry(++lastSeq, date, from, action, messageId, text, buttons, info).also(entries::add)

    fun history(afterSeq: Long): List<HistoryEntry> = entries.filter { it.seq > afterSeq }

    fun summary() = ChatSummary(id, owner, startedAt)
}
