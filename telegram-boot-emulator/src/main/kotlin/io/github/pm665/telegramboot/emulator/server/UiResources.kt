package io.github.pm665.telegramboot.emulator.server

/**
 * Static files of the emulator UI. They are kept outside of `META-INF/resources`, so that a host web application
 * never serves them on its own port.
 */
internal object UiResources {
    private const val LOCATION = "/io/github/pm665/telegramboot/emulator/ui/"

    class Resource(
        val contentType: String,
        val bytes: ByteArray,
    )

    private val files =
        mapOf(
            "/" to Pair("index.html", "text/html; charset=utf-8"),
            "/index.html" to Pair("index.html", "text/html; charset=utf-8"),
            "/app.js" to Pair("app.js", "text/javascript; charset=utf-8"),
            "/app.css" to Pair("app.css", "text/css; charset=utf-8"),
        )

    private val resources: Map<String, Resource> by lazy {
        files.mapValues { (_, file) ->
            val bytes = checkNotNull(UiResources::class.java.getResourceAsStream(LOCATION + file.first)) { "Missing UI file ${file.first}" }.use { it.readAllBytes() }
            Resource(file.second, bytes)
        }
    }

    fun find(path: String): Resource? = if (path in files) resources.getValue(path) else null
}
