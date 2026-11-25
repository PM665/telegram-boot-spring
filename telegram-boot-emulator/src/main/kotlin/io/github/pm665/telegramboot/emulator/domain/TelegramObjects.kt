package io.github.pm665.telegramboot.emulator.domain

import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.MessageEntity
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.chat.Chat
import org.telegram.telegrambots.meta.api.objects.location.Location
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow

/** Maps emulator state to Bot API objects, so that the wire format is the one the telegrambots client expects. */
internal object TelegramObjects {
    private val commandPattern = Regex("^/[A-Za-z0-9_]+(@[A-Za-z0-9_]+)?")

    fun botUser(
        id: Long,
        username: String,
    ): User =
        User
            .builder()
            .id(id)
            .isBot(true)
            .firstName(username)
            .userName(username)
            .canJoinGroups(false)
            .canReadAllGroupMessages(false)
            .supportInlineQueries(false)
            .build()

    fun user(profile: UserProfile): User =
        User
            .builder()
            .id(profile.id)
            .isBot(false)
            .firstName(profile.displayName)
            .userName(profile.username)
            .languageCode(profile.languageCode)
            .build()

    fun chat(chat: EmulatedChat): Chat =
        Chat
            .builder()
            .id(chat.id)
            .type("private")
            .firstName(chat.owner.displayName)
            .userName(chat.owner.username)
            .build()

    fun message(
        bot: User,
        chat: EmulatedChat,
        message: ChatMessage,
    ): Message {
        val builder =
            Message
                .builder()
                .messageId(message.messageId)
                .date(message.date.epochSecond.toInt())
                .chat(chat(chat))
                .from(if (message.from == Party.BOT) bot else user(chat.owner))
        if (message.photo != null) {
            builder.caption(message.text).photo(listOf(photoSize(message.photo)))
        } else {
            builder.text(message.text).entities(commandEntities(message.text))
        }
        message.location?.let { builder.location(Location.builder().latitude(it.latitude).longitude(it.longitude).build()) }
        if (message.buttons.isNotEmpty()) builder.replyMarkup(inlineKeyboard(message.buttons))
        return builder.build()
    }

    fun messageUpdate(
        updateId: Int,
        message: Message,
    ) = Update().apply {
        this.updateId = updateId
        this.message = message
    }

    fun callbackUpdate(
        updateId: Int,
        callbackQueryId: String,
        from: User,
        message: Message,
        data: String,
    ) = Update().apply {
        this.updateId = updateId
        this.callbackQuery =
            CallbackQuery().apply {
                id = callbackQueryId
                this.from = from
                this.message = message
                this.data = data
                chatInstance = message.chatId.toString()
            }
    }

    private fun commandEntities(text: String?): List<MessageEntity>? {
        val command = text?.let(commandPattern::find) ?: return null
        return listOf(MessageEntity.builder().type("bot_command").offset(0).length(command.value.length).build())
    }

    private fun photoSize(photo: PhotoFile) =
        PhotoSize
            .builder()
            .fileId(photo.fileId)
            .fileUniqueId(photo.fileUniqueId)
            .width(PHOTO_SIZE)
            .height(PHOTO_SIZE)
            .fileSize(photo.size)
            .build()

    private fun inlineKeyboard(buttons: List<List<Button>>) =
        InlineKeyboardMarkup(
            buttons.map { row ->
                InlineKeyboardRow(
                    row.map { InlineKeyboardButton.builder().text(it.text).callbackData(it.callbackData).url(it.url).build() },
                )
            },
        )

    private const val PHOTO_SIZE = 320
}
