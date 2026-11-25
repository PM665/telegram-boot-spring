package io.github.pm665.telegramboot.emulator.botapi

import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import java.nio.charset.StandardCharsets

/** Minimal `multipart/form-data` parser, enough for the Bot API upload requests. */
internal object Multipart {
    private val CRLF = "\r\n".toByteArray()
    private val HEADERS_END = "\r\n\r\n".toByteArray()
    private val boundaryPattern = Regex("boundary=(\"?)([^\";]+)\\1", RegexOption.IGNORE_CASE)
    private val namePattern = Regex("\\bname=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val filenamePattern = Regex("\\bfilename=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    class Part(
        val name: String,
        val filename: String?,
        val bytes: ByteArray,
    )

    fun boundary(contentType: String): String =
        boundaryPattern.find(contentType)?.groupValues?.get(2) ?: throw EmulatorException.badRequest("multipart boundary is missing")

    fun parse(
        body: ByteArray,
        boundary: String,
    ): List<Part> {
        val delimiter = "--$boundary".toByteArray(StandardCharsets.ISO_8859_1)
        val parts = mutableListOf<Part>()
        var position = indexOf(body, delimiter, 0).takeIf { it >= 0 } ?: throw EmulatorException.badRequest("malformed multipart body")
        while (true) {
            val start = position + delimiter.size
            if (body.startsWith("--".toByteArray(), start)) break
            val next = indexOf(body, delimiter, start).takeIf { it >= 0 } ?: throw EmulatorException.badRequest("malformed multipart body")
            parts += part(body, start + CRLF.size, next - CRLF.size)
            position = next
        }
        return parts
    }

    private fun part(
        body: ByteArray,
        from: Int,
        to: Int,
    ): Part {
        val headersEnd = indexOf(body, HEADERS_END, from).takeIf { it in from..to } ?: throw EmulatorException.badRequest("malformed multipart part")
        val headers = String(body, from, headersEnd - from, StandardCharsets.UTF_8)
        val disposition =
            headers.lineSequence().firstOrNull { it.startsWith("Content-Disposition", ignoreCase = true) }
                ?: throw EmulatorException.badRequest("multipart part without Content-Disposition")
        val name = namePattern.find(disposition)?.groupValues?.get(1) ?: throw EmulatorException.badRequest("multipart part without name")
        val filename = filenamePattern.find(disposition)?.groupValues?.get(1)
        return Part(name, filename, body.copyOfRange(headersEnd + HEADERS_END.size, to))
    }

    private fun ByteArray.startsWith(
        prefix: ByteArray,
        at: Int,
    ) = at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }

    private fun indexOf(
        data: ByteArray,
        pattern: ByteArray,
        from: Int,
    ): Int {
        for (i in from..data.size - pattern.size) {
            if (data.startsWith(pattern, i)) return i
        }
        return -1
    }
}
