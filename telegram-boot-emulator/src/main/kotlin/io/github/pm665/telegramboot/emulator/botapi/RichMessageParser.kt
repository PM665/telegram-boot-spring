package io.github.pm665.telegramboot.emulator.botapi

import io.github.pm665.telegramboot.emulator.domain.Button

/**
 * Reads the HTML of `sendRichMessage` produced by the library: paragraphs with text followed by
 * `<tg-button-row>` rows of `<tg-button type="callback_data" data="...">` buttons.
 */
internal object RichMessageParser {
    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val rowPattern = Regex("<tg-button-row\\b[^>]*>(.*?)</tg-button-row>", options)
    private val buttonPattern = Regex("<tg-button\\b([^>]*)>(.*?)</tg-button>", options)
    private val lineBreakPattern = Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE)
    private val tagPattern = Regex("<[^>]+>")

    data class RichMessage(
        val text: String,
        val buttons: List<List<Button>>,
    )

    fun parse(html: String): RichMessage {
        val buttons =
            rowPattern
                .findAll(html)
                .map { row -> buttonPattern.findAll(row.groupValues[1]).map { button(it.groupValues[1], it.groupValues[2]) }.toList() }
                .filter { it.isNotEmpty() }
                .toList()
        val text =
            html
                .replace(rowPattern, "")
                .replace(lineBreakPattern, "\n")
                .replace(tagPattern, "")
                .let(::unescape)
                .lines()
                .joinToString("\n") { it.trim() }
                .trim()
        return RichMessage(text, buttons)
    }

    private fun button(
        attributes: String,
        content: String,
    ): Button {
        val text = unescape(content.replace(tagPattern, "")).trim()
        val data = attribute(attributes, "data")
        return when (attribute(attributes, "type")) {
            null, "callback_data" -> Button(text, callbackData = data)
            else -> Button(text, url = attribute(attributes, "url") ?: attribute(attributes, "href"))
        }
    }

    private fun attribute(
        attributes: String,
        name: String,
    ): String? = Regex("\\b$name=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attributes)?.groupValues?.get(1)?.let(::unescape)

    private fun unescape(value: String) =
        value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
}
