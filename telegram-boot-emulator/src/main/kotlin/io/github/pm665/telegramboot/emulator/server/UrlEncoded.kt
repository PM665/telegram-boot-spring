package io.github.pm665.telegramboot.emulator.server

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** `application/x-www-form-urlencoded` values: query strings and form bodies. */
internal object UrlEncoded {
    fun parse(value: String?): Map<String, String> =
        value
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair -> decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', "")) }

    fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)
}
