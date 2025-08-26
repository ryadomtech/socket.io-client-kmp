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

package tech.ryadom.socketio.client.io

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.ryadom.socketio.client.engine.DefaultHttpClientFactory
import tech.ryadom.socketio.client.engine.HttpClientFactory
import tech.ryadom.socketio.client.util.KioLogger
import tech.ryadom.socketio.client.util.LogLevel

class Options : SocketManager.Options() {
    var forceNew = false
    var multiplex = true
}

/**
 * Race-free IO scope
 */
internal val lpScope = CoroutineScope(
    Dispatchers.Default.limitedParallelism(1)
)

private val managers = mutableMapOf<String, SocketManager>()

private class NaiveLogger : KioLogger {
    override fun log(level: LogLevel, message: String, cause: Throwable?) {
        println("[$level] IO: $message")
        cause?.printStackTrace()
    }
}

/**
 * Initializes and manages a Socket.IO connection.
 *
 * This function acts as the entry point for creating and managing Socket.IO client connections.
 * It handles the creation of `SocketManager` instances, which are responsible for the underlying
 * transport and communication with the server. It supports multiplexing, allowing multiple
 * sockets to share the same underlying connection if they connect to the same host and port.
 *
 * The behavior regarding connection reuse and creation is determined by the `options` parameter:
 * - If `options.forceNew` is `true`, a new connection will always be established, even if a
 *   compatible one already exists.
 * - If `options.multiplex` is `false`, a new connection will be established.
 * - If a `SocketManager` already exists for the given host and port, and the requested namespace
 *   is already in use by that manager, a new connection will be established.
 * - Otherwise, an existing `SocketManager` will be reused if available, or a new one will be
 *   created if no suitable manager exists.
 *
 * The created or reused `Socket` instance is then passed to the `block` lambda, allowing for
 * further configuration and event handling.
 *
 * This function operates within a dedicated coroutine scope (`lpScope`) with limited parallelism
 * to ensure thread-safe management of `SocketManager` instances.
 *
 * @param uri The URI of the Socket.IO server to connect to (e.g., "http://localhost:3000").
 * @param options Configuration options for the connection. See [Options] for details.
 *                Defaults to a new [Options] instance with default values.
 * @param logger A [KioLogger] instance for logging events related to the connection.
 *               Defaults to a [NaiveLogger] which prints to the console.
 * @param block A lambda function that will be executed with the created [Socket] instance.
 *              This is where you would typically set up event listeners and interact with the socket.
 * @return A [kotlinx.coroutines.Job] representing the asynchronous connection process.
 */
fun Socket.Companion.io(
    uri: String,
    options: Options = Options(),
    logger: KioLogger = NaiveLogger(),
    httpClientFactory: HttpClientFactory = DefaultHttpClientFactory(options, logger),
    block: Socket.() -> Unit
) = lpScope.launch {
    val url = Url(uri)
    val id = "${url.protocol}://${url.host}:${url.port}"

    val existingManager = managers[id]
    val namespacePath = if (url.segments.isEmpty()) "/" else url.encodedPath

    val shouldCreateNewConnection = options.forceNew || !options.multiplex ||
            (existingManager?.namespaceSockets?.containsKey(namespacePath) == true)

    val manager = if (shouldCreateNewConnection) {
        // If forceNew is true, or multiplex is false, or the namespace already exists for an existing manager,
        // create a new SocketManager instance.

        SocketManager(uri, logger, options, httpClientFactory).also {
            // If not multiplexing or forcing new, we might be replacing an old manager for this id
            // or creating a new one if it's a completely new connection.
            if (options.forceNew || !options.multiplex) managers[id] = it
        }
    } else {
        // Otherwise, reuse an existing manager or create a new one if it doesn't exist for this id.
        existingManager ?: SocketManager(uri, logger, options, httpClientFactory).also { managers[id] = it }
    }

    val socket = manager.socket(namespacePath, options.auth)
    block(socket)
}