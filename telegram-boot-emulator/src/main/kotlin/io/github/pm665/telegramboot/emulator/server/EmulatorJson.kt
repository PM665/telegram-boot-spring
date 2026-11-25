package io.github.pm665.telegramboot.emulator.server

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.pm665.telegramboot.emulator.domain.EmulatorException

/** JSON of the emulator. The mapper is private to the emulator and never exposed as a bean. */
internal object EmulatorJson {
    fun mapper(): ObjectMapper =
        ObjectMapper()
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun error(
        mapper: ObjectMapper,
        e: EmulatorException,
    ): JsonNode =
        mapper
            .createObjectNode()
            .put("ok", false)
            .put("error_code", e.status)
            .put("description", e.description)
}
