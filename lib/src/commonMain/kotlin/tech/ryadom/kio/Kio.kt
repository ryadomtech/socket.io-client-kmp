package tech.ryadom.kio

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import tech.ryadom.kio.dsl.KioDsl
import tech.ryadom.kio.engine.DefaultHttpClientFactory
import tech.ryadom.kio.engine.HttpClientFactory
import tech.ryadom.kio.io.Socket
import tech.ryadom.kio.io.SocketManager
import tech.ryadom.kio.io.SocketManagerOptions
import tech.ryadom.kio.util.KioLogger
import tech.ryadom.kio.util.LogLevel
import tech.ryadom.kio.util.NaiveLogger

class LoggingConfig {
    var logger: KioLogger = NaiveLogger
        private set

    var level: LogLevel = LogLevel.INFO
        private set

    var isLogKtorRequestsEnabled: Boolean = false
        private set

    fun logKtorRequests() {
        isLogKtorRequestsEnabled = true
    }

    fun logLevel(level: LogLevel) {
        this.level = level
    }

    fun logger(logger: KioLogger) {
        this.logger = logger
    }
}

class OptionsConfig : SocketManagerOptions() {
    var forceNew = false
    var multiplex = true
}

class SocketConfig {
    var logging = LoggingConfig()
        private set

    var options = OptionsConfig()
        private set

    var httpClientFactory: HttpClientFactory = DefaultHttpClientFactory(
        options = options,
        loggingConfig = logging
    )
        private set

    @KioDsl
    fun logging(f: LoggingConfig.() -> Unit) {
        f(logging)
    }

    @KioDsl
    fun options(f: OptionsConfig.() -> Unit) {
        f(options)
    }

    @KioDsl
    fun httpClientFactory(f: () -> HttpClientFactory) {
        httpClientFactory = f()
    }
}

/**
 * Race-free IO scope
 */
internal val lpScope = CoroutineScope(
    Dispatchers.Default.limitedParallelism(1)
)

private val socketManagers = mutableMapOf<String, SocketManager>()

@KioDsl
fun kioSocket(uri: String, f: SocketConfig.() -> Unit): Socket {
    val config = SocketConfig()
    config.f()

    val url = Url(uri)
    val id = "${url.protocol}://${url.host}:${url.port}"

    val existingManager = socketManagers[id]
    val namespacePath = if (url.segments.isEmpty()) "/" else url.encodedPath

    val options = config.options
    val factory = config.httpClientFactory

    val logger = KioLogger { level, message, cause ->
        if (level.isGreaterOrEqualsThan(config.logging.level)) {
            config.logging.logger.log(level, message, cause)
        }
    }

    val shouldCreateNewConnection = options.forceNew || !options.multiplex ||
            (existingManager?.namespaceSockets?.containsKey(namespacePath) == true)

    val manager = if (shouldCreateNewConnection) {
        // If forceNew is true, or multiplex is false, or the namespace already exists for an existing manager,
        // create a new SocketManager instance.

        SocketManager(uri, logger, options, factory).also {
            // If not multiplexing or forcing new, we might be replacing an old manager for this id
            // or creating a new one if it's a completely new connection.
            if (options.forceNew || !options.multiplex) socketManagers[id] = it
        }
    } else {
        // Otherwise, reuse an existing manager or create a new one if it doesn't exist for this id.
        existingManager ?: SocketManager(
            uri,
            logger,
            options,
            factory
        ).also { socketManagers[id] = it }
    }

    return manager.socket(namespacePath, options.auth)
}