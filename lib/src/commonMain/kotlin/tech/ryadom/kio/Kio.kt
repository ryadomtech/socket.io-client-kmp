/*
    MIT License

    Copyright (c) 2025 Ryadom Tech

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

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

/**
 * Creates and configures a `Socket.IO` client socket.
 *
 * This function serves as the primary entry point for establishing a connection to a Socket.IO server. It leverages a
 * DSL for configuration, allowing for detailed setup of connection options, logging, and underlying HTTP client behavior.
 *
 * It manages socket connections to efficiently reuse existing underlying transport connections (multiplexing)
 * whenever possible. A new transport connection is established only when necessary, such as when connecting to a
 * new host/port, when `forceNew` is `true`, or when multiplexing is disabled.
 *
 * Example usage:
 * ```kotlin
 * val socket = kioSocket("http://localhost:3000") {
 *     // Configure logging
 *     logging {
 *         logLevel(LogLevel.DEBUG)
 *     }
 *
 *     // Configure connection options
 *     options {
 *         auth = mapOf("token" to "your_auth_token")
 *         forceNew = false // Explicitly allow multiplexing
 *     }
 *
 *     // (Optional) Provide a custom HTTP client factory
 *     httpClientFactory {
 *         DefaultHttpClientFactory(options, logging)
 *     }
 * }
 *
 * socket.connect()
 * ```
 *
 * @param uri The URI of the Socket.IO server (e.g., "http://localhost:3000/"). The path component
 *   is used to determine the namespace (e.g., "/admin"). If no path is provided, it defaults to the root namespace "/".
 * @param f A lambda with [SocketConfig] as its receiver, used to configure the socket connection via a DSL.
 * @return A [Socket] instance configured and ready to connect.
 *
 */
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