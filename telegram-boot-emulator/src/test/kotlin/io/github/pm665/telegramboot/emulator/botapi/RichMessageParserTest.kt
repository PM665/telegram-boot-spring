package io.github.pm665.telegramboot.emulator.botapi

import io.github.pm665.telegramboot.emulator.domain.Button
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RichMessageParserTest {
    @Test
    fun `paragraphs become lines and every button row is kept`() {
        val rich =
            RichMessageParser.parse(
                "<p>First</p><p>Second<br/>line</p>" +
                    "<tg-button-row><tg-button type=\"callback_data\" data=\"/a\">A</tg-button></tg-button-row>" +
                    "<tg-button-row><tg-button data=\"/b\">B</tg-button><tg-button type=\"url\" url=\"https://example.com\">Site</tg-button></tg-button-row>",
            )

        assertThat(rich.text).isEqualTo("First\nSecond\nline")
        assertThat(rich.buttons).containsExactly(
            listOf(Button("A", "/a")),
            listOf(Button("B", "/b"), Button("Site", url = "https://example.com")),
        )
    }

    @Test
    fun `message without buttons is plain text`() {
        assertThat(RichMessageParser.parse("<p>Just &lt;text&gt;</p>")).isEqualTo(RichMessageParser.RichMessage("Just <text>", emptyList()))
    }
}
