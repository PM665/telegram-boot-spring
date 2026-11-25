package io.github.pm665.telegramboot.emulator.botapi

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import io.github.pm665.telegramboot.emulator.server.UrlEncoded
import java.nio.charset.StandardCharsets

/**
 * Bot API request parameters. Telegram accepts them in the query string, as JSON, as a form or as multipart form data;
 * the telegrambots client sends JSON, and multipart for uploads (`sendPhoto`).
 */
internal class RequestParams(
    private val mapper: ObjectMapper,
    private val fields: Map<String, JsonNode>,
    private val files: Map<String, FilePart> = emptyMap(),
) {
    fun string(name: String): String? = fields[name]?.takeUnless { it.isNull }?.let { if (it.isValueNode) it.asText() else it.toString() }

    fun required(name: String): String = string(name)?.takeIf { it.isNotEmpty() } ?: throw EmulatorException.badRequest("$name is required")

    fun int(name: String): Int? = string(name)?.let { it.toIntOrNull() ?: throw EmulatorException.badRequest("$name must be an integer") }

    fun requiredInt(name: String): Int = int(name) ?: throw EmulatorException.badRequest("$name is required")

    fun requiredDouble(name: String): Double =
        required(name).let { it.toDoubleOrNull() ?: throw EmulatorException.badRequest("$name must be a number") }

    fun boolean(name: String): Boolean = string(name).toBoolean()

    /** Private chat id. Channel usernames (`@channel`) are not supported by the emulator. */
    fun chatId(): Long = required("chat_id").let { it.toLongOrNull() ?: throw EmulatorException.badRequest("chat not found") }

    /** Object parameter; multipart and form requests carry objects as JSON strings. */
    fun json(name: String): JsonNode? =
        fields[name]?.takeUnless { it.isNull }?.let { node ->
            if (!node.isTextual) return node
            try {
                mapper.readTree(node.asText())
            } catch (_: JacksonException) {
                throw EmulatorException.badRequest("can't parse $name JSON object")
            }
        }

    fun file(name: String): FilePart? = files[name]

    class FilePart(
        val filename: String?,
        val bytes: ByteArray,
    )

    companion object {
        fun parse(
            mapper: ObjectMapper,
            rawQuery: String?,
            contentType: String?,
            body: ByteArray,
        ): RequestParams {
            val fields = LinkedHashMap<String, JsonNode>()
            val files = LinkedHashMap<String, FilePart>()
            fields += urlEncoded(rawQuery)
            val mediaType = contentType?.substringBefore(';')?.trim()?.lowercase()
            if (body.isNotEmpty()) {
                when (mediaType) {
                    "application/json" -> fields += jsonObject(mapper, body)
                    "application/x-www-form-urlencoded" -> fields += urlEncoded(String(body, StandardCharsets.UTF_8))
                    "multipart/form-data" -> {
                        val parts = Multipart.parse(body, Multipart.boundary(contentType))
                        parts.filter { it.filename == null }.forEach { fields[it.name] = TextNode(String(it.bytes, StandardCharsets.UTF_8)) }
                        parts.filter { it.filename != null }.forEach { files[it.name] = FilePart(it.filename, it.bytes) }
                    }
                    else -> throw EmulatorException.badRequest("unsupported content type $contentType")
                }
            }
            return RequestParams(mapper, fields, files)
        }

        private fun jsonObject(
            mapper: ObjectMapper,
            body: ByteArray,
        ): Map<String, JsonNode> {
            val node =
                try {
                    mapper.readTree(body)
                } catch (_: JacksonException) {
                    throw EmulatorException.badRequest("can't parse JSON request body")
                }
            if (!node.isObject) throw EmulatorException.badRequest("JSON request body must be an object")
            return node.properties().associate { it.key to it.value }
        }

        private fun urlEncoded(value: String?): Map<String, JsonNode> = UrlEncoded.parse(value).mapValues { TextNode(it.value) }
    }
}
