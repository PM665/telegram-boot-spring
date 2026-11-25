package io.github.pm665.telegramboot.emulator.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import io.github.pm665.telegramboot.emulator.botapi.BotApiHandler
import io.github.pm665.telegramboot.emulator.botapi.RequestParams
import io.github.pm665.telegramboot.emulator.domain.EmulatorException
import io.github.pm665.telegramboot.emulator.userapi.UserApiHandler
import org.slf4j.LoggerFactory
import java.io.IOException

/** Routes emulator requests: UI at `/`, Bot API at `/bot<token>/<method>`, user API at `/api`. */
internal class EmulatorRouter(
    private val botApi: BotApiHandler,
    private val userApi: UserApiHandler,
    private val mapper: ObjectMapper,
) : HttpHandler {
    private val log = LoggerFactory.getLogger(EmulatorRouter::class.java)

    override fun handle(exchange: HttpExchange) =
        exchange.use {
            try {
                val ui = UiResources.find(exchange.requestURI.rawPath)
                if (ui != null) {
                    sendUi(exchange, ui)
                } else {
                    val (status, body) = route(exchange)
                    send(exchange, status, body)
                }
            } catch (e: IOException) {
                log.debug("Telegram emulator connection failed: {}", e.message)
            } catch (e: Exception) {
                log.error("Telegram emulator failed to handle {} {}", exchange.requestMethod, exchange.requestURI.path, e)
                sendQuietly(exchange, 500, error(EmulatorException(500, "Internal Server Error: ${e.message}")))
            }
        }

    private fun route(exchange: HttpExchange): Pair<Int, JsonNode> {
        val botCall = BOT_API_PATH.matchEntire(exchange.requestURI.rawPath)
        if (botCall != null) {
            val (token, method) = botCall.destructured
            return botApi.handle(decode(token), decode(method)) {
                RequestParams.parse(
                    mapper,
                    exchange.requestURI.rawQuery,
                    exchange.requestHeaders.getFirst("Content-Type"),
                    exchange.requestBody.readAllBytes(),
                )
            }
        }
        val path = exchange.requestURI.rawPath.split('/').filter { it.isNotEmpty() }.map(::decode)
        if (path.firstOrNull() == API) {
            return userApi.handle(exchange.requestMethod, path.drop(1), exchange.requestURI.rawQuery) { exchange.requestBody.readAllBytes() }
        }
        return 404 to error(EmulatorException.notFound(exchange.requestURI.path))
    }

    private fun send(
        exchange: HttpExchange,
        status: Int,
        body: JsonNode,
    ) {
        val bytes = mapper.writeValueAsBytes(body)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun sendUi(
        exchange: HttpExchange,
        resource: UiResources.Resource,
    ) {
        if (exchange.requestMethod != "GET") {
            send(exchange, 405, error(EmulatorException(405, "Method Not Allowed: ${exchange.requestMethod} ${exchange.requestURI.path}")))
            return
        }
        exchange.responseHeaders.set("Content-Type", resource.contentType)
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.responseHeaders.set("X-Content-Type-Options", "nosniff")
        exchange.sendResponseHeaders(200, resource.bytes.size.toLong())
        exchange.responseBody.write(resource.bytes)
    }

    private fun sendQuietly(
        exchange: HttpExchange,
        status: Int,
        body: JsonNode,
    ) {
        try {
            send(exchange, status, body)
        } catch (_: IOException) {
            // the response may already be committed
        }
    }

    private fun error(e: EmulatorException): JsonNode = EmulatorJson.error(mapper, e)

    private fun decode(value: String) = UrlEncoded.decode(value)

    private companion object {
        const val API = "api"
        val BOT_API_PATH = Regex("^/bot([^/]+)/([^/]+)/?$")
    }
}
