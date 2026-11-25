package io.github.pm665.telegramboot.emulator.userapi

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import io.github.pm665.telegramboot.emulator.domain.TelegramEmulator
import io.github.pm665.telegramboot.emulator.domain.UserProfile
import io.github.pm665.telegramboot.emulator.server.UrlEncoded

/**
 * HTTP API of the user side of the chats, at `/api`:
 *
 * - `GET  /api/bots` registered bots
 * - `GET  /api/bots/{bot}/chats?userId=` chats of the user with the bot
 * - `POST /api/bots/{bot}/chats` `{userId, username?, firstName?, languageCode?}` starts the chat, sends `/start`
 * - `POST /api/bots/{bot}/chats/{chatId}/messages` `{userId, text}` sends a text message
 * - `POST /api/bots/{bot}/chats/{chatId}/callbacks` `{userId, messageId, data}` presses an inline button
 * - `GET  /api/bots/{bot}/chats/{chatId}/history?userId=&after=` chat history after the given `seq`
 *
 * Every action identifies the user by `userId`; only the user who started the chat acts in it.
 */
internal class UserApiHandler(
    private val emulator: TelegramEmulator,
    private val mapper: ObjectMapper,
) {
    /** Returns the HTTP status and the response body. */
    fun handle(
        method: String,
        path: List<String>,
        rawQuery: String?,
        body: () -> ByteArray,
    ): Pair<Int, JsonNode> =
        try {
            route(Request(method.uppercase(), path, UrlEncoded.parse(rawQuery), body))
        } catch (e: EmulatorException) {
            e.status to mapper.valueToTree(ErrorResponse(e.status, e.description))
        }

    private fun route(request: Request): Pair<Int, JsonNode> {
        val path = request.path
        return when {
            path == listOf("bots") -> request.get { emulator.bots().map(::BotResponse) }
            path.size == 3 && path[0] == "bots" && path[2] == "chats" -> chats(request, bot = path[1])
            path.size == 5 && path[0] == "bots" && path[2] == "chats" -> chat(request, bot = path[1], chatId = chatId(path[3]), resource = path[4])
            else -> throw EmulatorException.notFound("/api/${path.joinToString("/")}")
        }
    }

    private fun chats(
        request: Request,
        bot: String,
    ): Pair<Int, JsonNode> =
        when (request.method) {
            "GET" -> ok(emulator.chats(bot, request.queryUserId()).map(::ChatResponse))
            "POST" -> {
                val result = emulator.startChat(bot, request.json().user())
                val created = result.start != null
                (if (created) 201 else 200) to mapper.valueToTree(StartChatResponse(created, ChatResponse(result.chat), result.start))
            }
            else -> throw methodNotAllowed(request)
        }

    private fun chat(
        request: Request,
        bot: String,
        chatId: Long,
        resource: String,
    ): Pair<Int, JsonNode> =
        when (resource) {
            "messages" ->
                request.post { body -> emulator.sendMessage(bot, chatId, body.user(), body.text("text").orEmpty()) }

            "callbacks" ->
                request.post { body ->
                    emulator.pressButton(bot, chatId, body.user(), body.int("messageId"), body.text("data") ?: throw missing("data"))
                }

            "history" ->
                request.get {
                    val after = request.query["after"]?.let { it.toLongOrNull() ?: throw EmulatorException.badRequest("after must be a number") }
                    emulator.history(bot, chatId, request.queryUserId(), after ?: 0).map(::EntryResponse)
                }

            else -> throw EmulatorException.notFound("/api/${request.path.joinToString("/")}")
        }

    private fun Request.get(result: () -> Any): Pair<Int, JsonNode> = if (method == "GET") ok(result()) else throw methodNotAllowed(this)

    private fun Request.post(result: (JsonNode) -> Any): Pair<Int, JsonNode> = if (method == "POST") ok(result(json())) else throw methodNotAllowed(this)

    private fun Request.json(): JsonNode {
        val node =
            try {
                mapper.readTree(body())
            } catch (_: JacksonException) {
                throw EmulatorException.badRequest("request body must be JSON")
            }
        if (node == null || !node.isObject) throw EmulatorException.badRequest("request body must be a JSON object")
        return node
    }

    private fun Request.queryUserId(): Long = userId(query["userId"])

    private fun JsonNode.user() = UserProfile(userId(text("userId")), text("username"), text("firstName"), text("languageCode"))

    private fun JsonNode.text(field: String): String? = get(field)?.takeUnless { it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonNode.int(field: String): Int = text(field)?.let { it.toIntOrNull() ?: throw EmulatorException.badRequest("$field must be a number") } ?: throw missing(field)

    private fun userId(value: String?): Long = value?.let { it.toLongOrNull() ?: throw EmulatorException.badRequest("userId must be a number") } ?: throw missing("userId")

    private fun chatId(value: String): Long = value.toLongOrNull() ?: throw EmulatorException.badRequest("chat id must be a number")

    private fun ok(result: Any): Pair<Int, JsonNode> = 200 to mapper.valueToTree(result)

    private fun missing(field: String) = EmulatorException.badRequest("$field is required")

    private fun methodNotAllowed(request: Request) = EmulatorException(405, "Method Not Allowed: ${request.method} /api/${request.path.joinToString("/")}")

    private class Request(
        val method: String,
        val path: List<String>,
        val query: Map<String, String>,
        val body: () -> ByteArray,
    )
}
