package io.github.pm665.telegramboot.emulator.domain

/** Rejected request. [status] and [description] follow the Telegram Bot API error conventions. */
class EmulatorException(
    val status: Int,
    val description: String,
) : RuntimeException(description) {
    companion object {
        fun badRequest(reason: String) = EmulatorException(400, "Bad Request: $reason")

        fun unauthorized(reason: String? = null) = EmulatorException(401, listOfNotNull("Unauthorized", reason).joinToString(": "))

        fun forbidden(reason: String) = EmulatorException(403, "Forbidden: $reason")

        fun notFound(reason: String) = EmulatorException(404, "Not Found: $reason")
    }
}
