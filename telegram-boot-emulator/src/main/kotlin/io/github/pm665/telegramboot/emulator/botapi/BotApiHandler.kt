package io.github.pm665.telegramboot.emulator.botapi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.pm665.telegramboot.emulator.domain.BotCommandItem
import io.github.pm665.telegramboot.emulator.domain.Button
import io.github.pm665.telegramboot.emulator.domain.EmulatedBot
import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import io.github.pm665.telegramboot.emulator.domain.PhotoInput
import io.github.pm665.telegramboot.emulator.domain.TelegramEmulator
import io.github.pm665.telegramboot.emulator.server.EmulatorJson
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Telegram Bot API subset used by telegram-boot long polling bots, served at `/bot<token>/<method>`.
 * Responses use the Bot API envelope: `{"ok":true,"result":...}` or `{"ok":false,"error_code":...,"description":...}`.
 */
internal class BotApiHandler(
    private val emulator: TelegramEmulator,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(BotApiHandler::class.java)

    /** Returns the HTTP status and the response body. */
    fun handle(
        token: String,
        method: String,
        params: () -> RequestParams,
    ): Pair<Int, JsonNode> =
        try {
            200 to mapper.createObjectNode().put("ok", true).set(RESULT, mapper.valueToTree(call(token, method.lowercase(), params)))
        } catch (e: EmulatorException) {
            log.debug("Bot API {} rejected: {}", method, e.description)
            e.status to EmulatorJson.error(mapper, e)
        }

    private fun call(
        token: String,
        method: String,
        params: () -> RequestParams,
    ): Any {
        if (method !in SUPPORTED_METHODS) {
            log.warn("Bot API method {} is not supported by the Telegram emulator", method)
            throw EmulatorException.notFound("method $method is not supported by the Telegram emulator")
        }
        if (method == "getme") return emulator.register(token).user
        val bot = emulator.registeredBot(token)
        log.debug("Bot API {} from @{}", method, bot.username)
        return call(bot, method, params())
    }

    private fun call(
        bot: EmulatedBot,
        method: String,
        params: RequestParams,
    ): Any =
        when (method) {
            "deletewebhook" -> true

            "getupdates" ->
                bot.getUpdates(
                    offset = params.int("offset") ?: 0,
                    limit = (params.int("limit") ?: MAX_UPDATES).coerceIn(1, MAX_UPDATES),
                    timeout = Duration.ofSeconds((params.int("timeout") ?: 0).coerceIn(0, MAX_POLL_SECONDS).toLong()),
                )

            "sendmessage" -> bot.sendMessage(params.chatId(), params.string("text"), params.string("parse_mode"), params.buttons())

            "sendrichmessage" -> {
                val html = params.json("rich_message")?.get("html")?.asText().orEmpty()
                val rich = RichMessageParser.parse(html)
                if (rich.text.isBlank() && rich.buttons.isEmpty()) throw EmulatorException.badRequest("message text is empty")
                bot.sendRichMessage(params.chatId(), rich.text, rich.buttons)
            }

            "sendphoto" -> bot.sendPhoto(params.chatId(), params.photo(), params.string("caption"), params.buttons())

            "sendlocation" -> bot.sendLocation(params.chatId(), params.requiredDouble("latitude"), params.requiredDouble("longitude"))

            "editmessagetext" -> bot.editMessageText(params.chatId(), params.messageId(), params.string("text"), params.buttons())

            "editmessagereplymarkup" -> bot.editMessageReplyMarkup(params.chatId(), params.messageId(), params.buttons())

            "deletemessage" -> bot.deleteMessage(params.chatId(), params.messageId()).let { true }

            "pinchatmessage" -> bot.pinMessage(params.chatId(), params.messageId()).let { true }

            "unpinchatmessage" -> bot.unpinMessage(params.chatId(), params.int("message_id")).let { true }

            "answercallbackquery" ->
                bot
                    .answerCallbackQuery(params.required("callback_query_id"), params.string("text"), params.boolean("show_alert"))
                    .let { true }

            "setmycommands" -> bot.setCommands(params.scope(), params.string("language_code"), params.commands()).let { true }

            "getmycommands" -> bot.commands(params.scope(), params.string("language_code"))

            else -> error("Unhandled method $method")
        }

    /** Uploaded file part, an `attach://<part>` reference to it, or a `file_id` of a photo sent before. */
    private fun RequestParams.photo(): PhotoInput {
        val reference = string("photo")
        val upload = file("photo") ?: reference?.takeIf { it.startsWith(ATTACH) }?.let { file(it.removePrefix(ATTACH)) }
        return when {
            upload != null -> PhotoInput.Upload(upload.filename, upload.bytes)
            reference.isNullOrEmpty() -> throw EmulatorException.badRequest("photo is required")
            reference.startsWith(ATTACH) -> throw EmulatorException.badRequest("file of $reference is not found in the request")
            else -> PhotoInput.FileId(reference)
        }
    }

    private fun RequestParams.messageId(): Int =
        int("message_id") ?: throw EmulatorException.badRequest(
            if (string("inline_message_id") != null) "inline messages are not supported by the emulator" else "message_id is required",
        )

    /** Inline keyboard of `reply_markup`. Reply keyboards are not emulated. */
    private fun RequestParams.buttons(): List<List<Button>> =
        json("reply_markup")?.get("inline_keyboard")?.map { row ->
            row.map { Button(it.text("text").orEmpty(), it.text("callback_data"), it.text("url")) }
        } ?: emptyList()

    private fun RequestParams.scope(): String = json("scope")?.text("type") ?: "default"

    private fun RequestParams.commands(): List<BotCommandItem> =
        json("commands")?.map { BotCommandItem(it.text("command").orEmpty(), it.text("description").orEmpty()) }
            ?: throw EmulatorException.badRequest("commands are required")

    private fun JsonNode.text(field: String): String? = get(field)?.takeUnless { it.isNull }?.asText()

    private companion object {
        const val RESULT = "result"
        const val ATTACH = "attach://"
        const val MAX_UPDATES = 100
        const val MAX_POLL_SECONDS = 50
        val SUPPORTED_METHODS =
            setOf(
                "getme",
                "deletewebhook",
                "getupdates",
                "sendmessage",
                "sendrichmessage",
                "sendphoto",
                "sendlocation",
                "editmessagetext",
                "editmessagereplymarkup",
                "deletemessage",
                "pinchatmessage",
                "unpinchatmessage",
                "answercallbackquery",
                "setmycommands",
                "getmycommands",
            )
    }
}
