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

package tech.ryadom.kio.engine.transports

import io.ktor.util.date.GMTDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.hildan.socketio.EngineIOPacket
import tech.ryadom.kio.engine.State
import tech.ryadom.kio.util.Emitter
import tech.ryadom.kio.util.KioLogger
import tech.ryadom.kio.util.QSParsingUtils

/**
 * Abstract base class for all transport implementations.
 *
 * @param name The name of the transport (e.g., "websocket", "polling")
 * @param options Configuration options for the transport
 * @param rawMessage Whether to handle messages as raw binary data
 */
abstract class Transport(
    val name: String,
    internal val options: Options,
    protected val rawMessage: Boolean,
    protected val logger: KioLogger
) : Emitter() {

    /**
     * Default IO scope
     */
    protected val ioScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    /**
     * Configuration options for the transport.
     */
    open class Options {
        var isSecure: Boolean = false
        var hostname: String = ""
        var port: Int = -1
        var path: String = ""
        var isTimestampRequests: Boolean = false
        var timestampParam: String = "t"
        var query: MutableMap<String, String> = mutableMapOf()
        var extraHeaders: Map<String, List<String>> = mapOf()
        var isTrustAllCerts: Boolean = false
    }

    protected var state: State = State.INIT
    internal var isWritable: Boolean = false

    /**
     * Opens the transport connection.
     * @return The transport instance for chaining
     */
    fun open(): Transport {
        if (state == State.CLOSED || state == State.INIT) {
            state = State.OPENING
            doOpen()
        }

        return this
    }

    /**
     * Sends a list of packets through the transport.
     * @param packets The packets to send
     * @throws IllegalStateException if transport is not open
     */
    fun send(packets: List<EngineIOPacket<*>>) {
        require(state == State.OPEN) { "Transport not open" }
        doSend(packets)
    }

    /**
     * Closes the transport connection.
     * @return The transport instance for chaining
     */
    fun close(): Transport {
        if (state == State.OPENING || state == State.OPEN) {
            val fromOpenState = state == State.OPEN
            state = State.CLOSING
            doClose(fromOpenState)
        }

        return this
    }

    /**
     * Pauses the transport, buffering outgoing packets until resumed.
     * @param onPause Callback invoked when pause is complete
     */
    abstract fun pause(onPause: () -> Unit)

    /**
     * Called when the transport connection is opened.
     */
    protected fun onOpen() {
        if (state == State.OPENING || state == State.CLOSING) {
            state = State.OPEN
            isWritable = true
            emit(EVENT_OPEN)
        }
    }

    /**
     * Called when a packet is received.
     * @param packet The received packet
     */
    protected fun onPacket(packet: EngineIOPacket<*>) {
        emit(EVENT_PACKET, packet)
    }

    /**
     * Called when an error occurs.
     * @param msg Error message
     */
    internal fun onError(msg: String) {
        emit(EVENT_ERROR, msg)
    }

    /**
     * Called when the transport connection is closed.
     */
    protected fun onClose() {
        state = State.CLOSED
        emit(EVENT_CLOSE)
    }

    /**
     * Transport-specific implementation of opening the connection.
     */
    protected abstract fun doOpen()

    /**
     * Transport-specific implementation of sending packets.
     * @param packets The packets to send
     */
    protected abstract fun doSend(packets: List<EngineIOPacket<*>>)

    /**
     * Transport-specific implementation of closing the connection.
     * @param fromOpenState Whether the connection was open when close was called
     */
    protected abstract fun doClose(fromOpenState: Boolean)

    /**
     * Constructs the URI for the transport connection.
     * @param secureSchema The URI scheme for secure connections (e.g., "https", "wss")
     * @param insecureSchema The URI scheme for insecure connections (e.g., "http", "ws")
     * @return The constructed URI string
     */
    protected fun uri(secureSchema: String, insecureSchema: String): String {
        val query = HashMap(options.query)
        val schema = if (options.isSecure) secureSchema else insecureSchema

        val port = buildString {
            if (options.port > 0 && ((options.isSecure && options.port != 443) ||
                        (!options.isSecure && options.port != 80))
            ) {
                append(":").append(options.port)
            }
        }

        if (options.isTimestampRequests) {
            query[options.timestampParam] = GMTDate().timestamp.toString(36)
        }

        val derivedQuery = QSParsingUtils.encode(query).takeIf { it.isNotEmpty() }
            ?.let { "?$it" }
            ?: ""

        val hostname = if (options.hostname.contains(":")) {
            "[${options.hostname}]"
        } else {
            options.hostname
        }

        return "$schema://$hostname$port${options.path}$derivedQuery"
    }

    companion object {
        const val EVENT_OPEN: String = "open"
        const val EVENT_CLOSE: String = "close"
        const val EVENT_PACKET: String = "packet"
        const val EVENT_DRAIN: String = "drain"
        const val EVENT_ERROR: String = "error"
        const val EVENT_REQUEST_HEADERS: String = "requestHeaders"
        const val EVENT_RESPONSE_HEADERS: String = "responseHeaders"
    }
}