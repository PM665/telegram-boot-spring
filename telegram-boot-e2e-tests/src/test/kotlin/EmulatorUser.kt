import com.jayway.jsonpath.JsonPath
import net.minidev.json.JSONValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

typealias Entry = Map<String, Any?>

/** A Telegram user acting through the user API of the Telegram emulator. */
class EmulatorUser(
    private val baseUrl: String,
    val id: Long,
    val username: String? = null,
    private val languageCode: String? = null,
) {
    private val http = HttpClient.newHttpClient()

    fun bots(): List<Map<String, Any?>> = JsonPath.read(request("GET", "/api/bots").body(), "$")

    fun startChat(bot: String) = request("POST", "/api/bots/$bot/chats", profile())

    fun send(
        bot: String,
        text: String,
    ) = request("POST", "/api/bots/$bot/chats/$id/messages", profile() + ("text" to text))

    fun press(
        bot: String,
        messageId: Int,
        data: String,
    ) = request("POST", "/api/bots/$bot/chats/$id/callbacks", profile() + mapOf("messageId" to messageId, "data" to data))

    fun historyResponse(bot: String) = request("GET", "/api/bots/$bot/chats/$id/history?userId=$id")

    fun history(bot: String): List<Entry> = JsonPath.read(historyResponse(bot).body(), "$")

    /** Waits for the bot to process the update: the first history entry matching the predicate. */
    fun awaitEntry(
        bot: String,
        predicate: (Entry) -> Boolean,
    ): Entry {
        val deadline = Instant.now().plusSeconds(10)
        while (true) {
            history(bot).firstOrNull(predicate)?.let { return it }
            check(Instant.now().isBefore(deadline)) { "No matching entry in the chat of user $id with @$bot: ${history(bot)}" }
            Thread.sleep(100)
        }
    }

    private fun profile(): Map<String, Any> = listOfNotNull("userId" to id, username?.let { "username" to it }, languageCode?.let { "languageCode" to it }).toMap()

    private fun request(
        method: String,
        path: String,
        body: Map<String, Any>? = null,
    ): HttpResponse<String> {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(JSONValue.toJSONString(it)) } ?: HttpRequest.BodyPublishers.noBody()
        val request =
            HttpRequest
                .newBuilder(URI.create("$baseUrl$path"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
