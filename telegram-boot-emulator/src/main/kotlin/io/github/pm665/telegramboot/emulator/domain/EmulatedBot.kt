package io.github.pm665.telegramboot.emulator.domain

import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A bot registered in the emulator with its private chats and pending updates.
 *
 * Every chat change happens under the bot lock, so chat history is ordered by the time the emulator received
 * the request. Only users start chats: a bot writing to a chat nobody started is rejected like in Telegram.
 */
internal class EmulatedBot(
    val id: Long,
    username: String,
    token: String,
    val registeredAt: Instant,
    private val clock: Clock,
) {
    @Volatile
    var username: String = username
        private set

    @Volatile
    var token: String = token
        private set

    private val lock = ReentrantLock()
    private val updates = UpdateQueue(clock)
    private val chats = LinkedHashMap<Long, EmulatedChat>()
    private val callbackQueries = HashMap<String, CallbackSource>()
    private val photos = HashMap<String, PhotoFile>()
    private val commands = HashMap<String, List<BotCommandItem>>()
    private var lastCallbackQueryId = 0
    private var lastFileId = 0

    val user: User get() = TelegramObjects.botUser(id, username)

    fun summary() = BotSummary(id, username, registeredAt, updates.isPolling(ONLINE_GRACE))

    fun rename(
        username: String,
        token: String,
    ) {
        this.username = username
        this.token = token
    }

    // ---- user side ----

    /** Creates the chat and sends `/start`, as the Start button in Telegram does. Does nothing for a started chat. */
    fun startChat(user: UserProfile): StartChatResult =
        lock.withLock {
            val existing = chats[user.id]
            if (existing != null) {
                existing.owner = user.orElse(existing.owner)
                StartChatResult(existing.summary(), null)
            } else {
                val chat = EmulatedChat(user, clock.instant())
                chats[chat.id] = chat
                StartChatResult(chat.summary(), userMessage(chat, START_COMMAND))
            }
        }

    fun sendUserMessage(
        chatId: Long,
        user: UserProfile,
        text: String,
    ): UserActionResult =
        lock.withLock {
            if (text.isBlank()) throw EmulatorException.badRequest("message text is empty")
            userMessage(ownedChat(chatId, user), text)
        }

    fun pressButton(
        chatId: Long,
        user: UserProfile,
        messageId: Int,
        callbackData: String,
    ): UserActionResult =
        lock.withLock {
            val chat = ownedChat(chatId, user)
            val message =
                chat.message(messageId)?.takeIf { it.from == Party.BOT }
                    ?: throw EmulatorException.badRequest("bot message $messageId not found in chat $chatId")
            val button =
                message.button(callbackData)
                    ?: throw EmulatorException.badRequest("message $messageId has no button with callback data '$callbackData'")
            val callbackQueryId = "cbq-$id-${++lastCallbackQueryId}"
            callbackQueries[callbackQueryId] = CallbackSource(chat.id, messageId)
            val entry = chat.record(Party.USER, HistoryAction.CALLBACK, clock.instant(), messageId, button.text, info = callbackData)
            val botMessage = TelegramObjects.message(this.user, chat, message)
            val updateId =
                updates.push { updateId ->
                    TelegramObjects.callbackUpdate(updateId, callbackQueryId, TelegramObjects.user(chat.owner), botMessage, callbackData)
                }
            UserActionResult(chat.id, entry.seq, updateId, messageId, callbackQueryId)
        }

    fun history(
        chatId: Long,
        userId: Long,
        afterSeq: Long = 0,
    ): List<HistoryEntry> = lock.withLock { ownedChat(chatId, userId).history(afterSeq) }

    fun chats(userId: Long): List<ChatSummary> = lock.withLock { listOfNotNull(chats[userId]?.summary()) }

    // ---- bot side ----

    fun getUpdates(
        offset: Int,
        limit: Int,
        timeout: Duration,
    ): List<Update> = updates.poll(offset, limit, timeout)

    fun sendMessage(
        chatId: Long,
        text: String?,
        parseMode: String?,
        buttons: List<List<Button>>,
    ): Message =
        lock.withLock {
            if (text.isNullOrBlank()) throw EmulatorException.badRequest("message text is empty")
            botMessage(chatId, HistoryAction.TEXT, text, buttons, info = parseMode?.let { "parse mode $it" })
        }

    fun sendRichMessage(
        chatId: Long,
        text: String,
        buttons: List<List<Button>>,
    ): Message = lock.withLock { botMessage(chatId, HistoryAction.RICH_MESSAGE, text, buttons) }

    fun sendPhoto(
        chatId: Long,
        photo: PhotoInput,
        caption: String?,
        buttons: List<List<Button>>,
    ): Message =
        lock.withLock {
            val chat = botChat(chatId)
            val file =
                when (photo) {
                    is PhotoInput.Upload -> {
                        val number = ++lastFileId
                        PhotoFile("photo-$id-$number", "unique-$id-$number", photo.filename, photo.bytes.size)
                            .also { photos[it.fileId] = it }
                    }

                    is PhotoInput.FileId -> {
                        photos[photo.fileId] ?: throw EmulatorException.badRequest("wrong file identifier/HTTP URL specified")
                    }
                }
            val info = listOfNotNull(file.name, file.size?.let { "$it bytes" }).joinToString(", ").ifEmpty { file.fileId }
            botMessage(chat, HistoryAction.PHOTO, caption, buttons, info, photo = file)
        }

    fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
    ): Message =
        lock.withLock {
            botMessage(
                botChat(chatId),
                HistoryAction.LOCATION,
                null,
                emptyList(),
                info = "$latitude, $longitude",
                location = ChatMessage.Location(latitude, longitude),
            )
        }

    /** As in Telegram, the inline keyboard is removed unless it is passed again. */
    fun editMessageText(
        chatId: Long,
        messageId: Int,
        text: String?,
        buttons: List<List<Button>>,
    ): Message =
        lock.withLock {
            if (text.isNullOrBlank()) throw EmulatorException.badRequest("message text is empty")
            val chat = botChat(chatId)
            val message = editableMessage(chat, messageId)
            if (!message.action.isTextMessage) throw EmulatorException.badRequest("there is no text in the message to edit")
            message.text = text
            message.buttons = buttons
            chat.record(Party.BOT, HistoryAction.EDIT_TEXT, clock.instant(), messageId, text, buttons)
            TelegramObjects.message(user, chat, message)
        }

    fun editMessageReplyMarkup(
        chatId: Long,
        messageId: Int,
        buttons: List<List<Button>>,
    ): Message =
        lock.withLock {
            val chat = botChat(chatId)
            val message = editableMessage(chat, messageId)
            message.buttons = buttons
            chat.record(Party.BOT, HistoryAction.EDIT_MARKUP, clock.instant(), messageId, buttons = buttons)
            TelegramObjects.message(user, chat, message)
        }

    fun deleteMessage(
        chatId: Long,
        messageId: Int,
    ) = lock.withLock {
        val chat = botChat(chatId)
        val message = chat.message(messageId) ?: throw EmulatorException.badRequest("message to delete not found")
        message.deleted = true
        chat.record(Party.BOT, HistoryAction.DELETE, clock.instant(), messageId)
    }

    fun pinMessage(
        chatId: Long,
        messageId: Int,
    ) = lock.withLock {
        val chat = botChat(chatId)
        chat.message(messageId) ?: throw EmulatorException.badRequest("message to pin not found")
        chat.record(Party.BOT, HistoryAction.PIN, clock.instant(), messageId)
    }

    fun unpinMessage(
        chatId: Long,
        messageId: Int?,
    ) = lock.withLock {
        val chat = botChat(chatId)
        if (messageId != null) chat.message(messageId) ?: throw EmulatorException.badRequest("message to unpin not found")
        chat.record(Party.BOT, HistoryAction.UNPIN, clock.instant(), messageId, info = if (messageId == null) "latest pinned message" else null)
    }

    fun answerCallbackQuery(
        callbackQueryId: String,
        text: String?,
        showAlert: Boolean,
    ) = lock.withLock {
        val source =
            callbackQueries.remove(callbackQueryId)
                ?: throw EmulatorException.badRequest("query is too old and response timeout expired or query ID is invalid")
        val chat = checkNotNull(chats[source.chatId])
        chat.record(
            Party.BOT,
            HistoryAction.CALLBACK_ANSWER,
            clock.instant(),
            source.messageId,
            text?.takeIf { it.isNotEmpty() },
            info = if (showAlert) "alert" else null,
        )
    }

    fun setCommands(
        scope: String,
        languageCode: String?,
        items: List<BotCommandItem>,
    ) = lock.withLock { commands[commandsKey(scope, languageCode)] = items.toList() }

    fun commands(
        scope: String,
        languageCode: String?,
    ): List<BotCommandItem> = lock.withLock { commands[commandsKey(scope, languageCode)].orEmpty() }

    fun close() = updates.close()

    // ---- internals, called under the lock ----

    private fun userMessage(
        chat: EmulatedChat,
        text: String,
    ): UserActionResult {
        val message = chat.addMessage(Party.USER, HistoryAction.TEXT, clock.instant(), text)
        val entry = chat.record(Party.USER, HistoryAction.TEXT, message.date, message.messageId, text)
        val telegramMessage = TelegramObjects.message(user, chat, message)
        val updateId = updates.push { TelegramObjects.messageUpdate(it, telegramMessage) }
        return UserActionResult(chat.id, entry.seq, updateId, message.messageId)
    }

    private fun botMessage(
        chatId: Long,
        action: HistoryAction,
        text: String?,
        buttons: List<List<Button>>,
        info: String? = null,
    ): Message = botMessage(botChat(chatId), action, text, buttons, info)

    private fun botMessage(
        chat: EmulatedChat,
        action: HistoryAction,
        text: String?,
        buttons: List<List<Button>>,
        info: String? = null,
        photo: PhotoFile? = null,
        location: ChatMessage.Location? = null,
    ): Message {
        val message = chat.addMessage(Party.BOT, action, clock.instant(), text, buttons, photo, location)
        chat.record(Party.BOT, action, message.date, message.messageId, text, buttons, info)
        return TelegramObjects.message(user, chat, message)
    }

    private fun ownedChat(
        chatId: Long,
        user: UserProfile,
    ): EmulatedChat = ownedChat(chatId, user.id).also { it.owner = user.orElse(it.owner) }

    private fun ownedChat(
        chatId: Long,
        userId: Long,
    ): EmulatedChat {
        val chat = chats[chatId] ?: throw EmulatorException.notFound("chat $chatId with @$username is not started")
        if (chat.owner.id != userId) throw EmulatorException.forbidden("chat $chatId belongs to another user")
        return chat
    }

    private fun botChat(chatId: Long): EmulatedChat = chats[chatId] ?: throw EmulatorException.forbidden("bot can't initiate conversation with a user")

    private fun editableMessage(
        chat: EmulatedChat,
        messageId: Int,
    ): ChatMessage {
        val message = chat.message(messageId) ?: throw EmulatorException.badRequest("message to edit not found")
        if (message.from != Party.BOT) throw EmulatorException.badRequest("message can't be edited")
        return message
    }

    private fun commandsKey(
        scope: String,
        languageCode: String?,
    ) = "$scope:${languageCode.orEmpty()}"

    private data class CallbackSource(
        val chatId: Long,
        val messageId: Int,
    )

    companion object {
        const val START_COMMAND = "/start"
        private val ONLINE_GRACE: Duration = Duration.ofSeconds(3)
    }
}
