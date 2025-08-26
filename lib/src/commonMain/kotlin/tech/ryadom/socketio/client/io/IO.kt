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
import tech.ryadom.socketio.client.util.KioLogger

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

fun Socket.Companion.io(
    uri: String,
    options: Options = Options(),
    logger: KioLogger,
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

        SocketManager(uri, logger, options).also {
            // If not multiplexing or forcing new, we might be replacing an old manager for this id
            // or creating a new one if it's a completely new connection.
            if (options.forceNew || !options.multiplex) managers[id] = it
        }
    } else {
        // Otherwise, reuse an existing manager or create a new one if it doesn't exist for this id.
        existingManager ?: SocketManager(uri, logger, options).also { managers[id] = it }
    }

    val socket = manager.socket(namespacePath, options.auth)
    block(socket)
}