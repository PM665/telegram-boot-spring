package io.github.pm665.telegramboot.emulator.server

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Embedded HTTP server of the emulator. It runs on its own port, independent of the application's web stack,
 * so it is available while the Spring context is still starting bots.
 */
class TelegramEmulatorServer(
    private val host: String,
    private val requestedPort: Int,
    private val handler: HttpHandler,
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    val port: Int
        get() = checkNotNull(server) { "Telegram emulator is not started" }.address.port

    /** Host bots should connect to: a wildcard bind address is not connectable. */
    val clientHost: String
        get() = if (host in WILDCARD_HOSTS) "127.0.0.1" else host

    val baseUrl: String
        get() = "http://${if (':' in clientHost) "[$clientHost]" else clientHost}:$port"

    @Synchronized
    fun start() {
        if (server != null) return
        val threads = Executors.newVirtualThreadPerTaskExecutor()
        server =
            HttpServer.create(InetSocketAddress(host, requestedPort), 0).apply {
                executor = threads
                createContext("/", handler)
                start()
            }
        executor = threads
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    private companion object {
        val WILDCARD_HOSTS = setOf("0.0.0.0", "::", "[::]")
    }
}
