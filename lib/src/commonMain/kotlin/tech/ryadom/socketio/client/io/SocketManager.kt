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

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hildan.socketio.EngineIOPacket
import tech.ryadom.socketio.client.engine.Engine
import tech.ryadom.socketio.client.engine.HttpClientFactory
import tech.ryadom.socketio.client.engine.State
import tech.ryadom.socketio.client.util.Emitter
import tech.ryadom.socketio.client.util.KioLogger
import tech.ryadom.socketio.client.util.On
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * The `SocketManager` class is responsible for managing the connection to a Socket.IO server.
 * It handles the underlying Engine.IO connection, including reconnection logic and event emission.
 *
 * This class acts as a central point for creating and managing multiple `Socket` instances
 * for different namespaces, all sharing the same underlying connection.
 *
 * @property uri The URI of the Socket.IO server.
 * @property logger A [KioLogger] instance for logging messages.
 * @property options The configuration options for the connection. See [SocketManager.Options].
 *
 * @see Socket
 * @see Engine
 * @see Options
 */
class SocketManager(
    private val uri: String,
    private val logger: KioLogger,
    private val options: Options,
    private val httpClientFactory: HttpClientFactory
) : Emitter() {

    /**
     * Configuration options for the [SocketManager].
     *
     * @property isReconnection Whether to enable reconnection. Default is `true`.
     * @property reconnectionAttempts The maximum number of reconnection attempts. Default is `Int.MAX_VALUE`.
     * @property reconnectionDelay The initial delay before attempting to reconnect. Default is `1.seconds`.
     * @property reconnectionDelayMax The maximum delay between reconnection attempts. Default is `5.seconds`.
     * @property randomizationFactor A factor to randomize the reconnection delay. Default is `0.5`.
     * @property auth A map of authentication data to be sent with the connection request. Default is an empty map.
     * @property timeout The connection timeout duration. Default is `20.seconds`.
     */
    open class Options : Engine.Options() {
        internal lateinit var backoff: Backoff

        var isReconnection = true

        var reconnectionAttempts: Int = Int.MAX_VALUE
        var reconnectionDelay: Duration = 5.seconds
            set(value) {
                if (::backoff.isInitialized) {
                    backoff.min = value.inWholeMilliseconds
                }

                field = value
            }

        var reconnectionDelayMax: Duration = 10.seconds
            set(value) {
                if (::backoff.isInitialized) {
                    backoff.max = value.inWholeMilliseconds
                }

                field = value
            }

        var randomizationFactor: Double = 0.5
            set(value) {
                if (::backoff.isInitialized) {
                    backoff.jitter = value
                }

                field = value
            }

        var auth: Map<String, String> = mapOf()
        var timeout: Duration = 20.seconds
    }

    internal var engine: Engine? = null
    internal var isReconnecting = false
        private set

    internal var state = State.INIT
        private set

    internal val namespaceSockets = mutableMapOf<String, Socket>()

    private val eventHandles = arrayListOf<On.Handle>()
    private var shouldSkipReconnect = false

    init {
        if (options.path.isEmpty()) {
            options.path = "/socket.io/"
        }

        options.backoff = Backoff(
            min = options.reconnectionDelay.inWholeMilliseconds,
            max = options.reconnectionDelayMax.inWholeMilliseconds,
            jitter = options.randomizationFactor
        )
    }

    fun setReconnectionDelay(delay: Duration) {
        options.reconnectionDelay = delay
    }

    fun setReconnectionDelayMax(delayMax: Duration) {
        options.reconnectionDelayMax = delayMax
    }

    fun setRandomizationFactor(factor: Double) {
        options.randomizationFactor = factor
    }

    fun open(callback: ((String) -> Unit)? = null) {
        logger.info { "Opening" }
        if (state != State.INIT && state != State.CLOSED) {
            return
        }

        val socket = Engine(uri, options, logger, httpClientFactory)
        engine = socket
        state = State.OPENING
        shouldSkipReconnect = false

        socket.on(Engine.EVENT_TRANSPORT) {
            emit(EVENT_TRANSPORT, *it)
        }

        val onOpenEvent = On.on(socket, Engine.EVENT_OPEN) {
            onOpen()
            callback?.invoke("")
        }

        val onErrorEvent = On.on(socket, Engine.EVENT_ERROR) {
            clearEventHandles()
            state = State.CLOSED
            emit(EVENT_ERROR, *it)

            callback?.invoke("Connection error") ?: maybeReconnectOnOpen()
        }

        val onTimeout = {
            onOpenEvent.destroy()
            socket.close()
            socket.emit(Engine.EVENT_ERROR, "timeout")
        }

        if (options.timeout == Duration.ZERO) {
            onTimeout()
            return
        } else {
            val job = lpScope.launch {
                delay(options.timeout)
                onTimeout()
            }

            eventHandles.add { job.cancel() }
        }

        eventHandles.add(onOpenEvent)
        eventHandles.add(onErrorEvent)

        socket.open()
    }

    internal fun close() {
        shouldSkipReconnect = true
        isReconnecting = false
        clearEventHandles()
        options.backoff.reset()
        state = State.CLOSED
        engine?.close()
        engine = null
    }

    private fun onOpen() {
        if (state != State.OPENING) {
            return
        }

        clearEventHandles()
        state = State.OPEN
        emit(EVENT_OPEN)

        val socket = engine ?: return
        eventHandles.add(
            On.on(socket, Engine.EVENT_DATA) { args ->
                if (args.isNotEmpty()) {
                    emit(EVENT_PACKET, args[0])
                }
            }
        )

        eventHandles.add(
            On.on(socket, Engine.EVENT_ERROR) { args ->
                if (args.isNotEmpty() && args[0] is String) {
                    onError(args[0] as String)
                } else {
                    onError("EngineSocket error")
                }
            }
        )

        eventHandles.add(
            On.on(socket, Engine.EVENT_CLOSE) { args ->
                if (args.isNotEmpty() && args[0] is String) {
                    onClose(args[0] as String)
                } else {
                    onClose("EngineSocket close")
                }
            }
        )
    }

    private fun clearEventHandles() {
        eventHandles.forEach { it.destroy() }
        eventHandles.clear()
    }

    private fun onError(error: String) {
        emit(EVENT_ERROR, error)
    }

    private fun onClose(reason: String) {
        clearEventHandles()
        options.backoff.reset()
        state = State.CLOSED
        emit(EVENT_CLOSE, reason)

        if (options.isReconnection && !shouldSkipReconnect) {
            reconnect()
        }
    }

    private fun maybeReconnectOnOpen() {
        if (!isReconnecting && options.isReconnection && options.backoff.attempts == 0) {
            reconnect()
        }
    }

    private fun reconnect() {
        if (isReconnecting || shouldSkipReconnect) {
            return
        }

        if (options.backoff.attempts >= options.reconnectionAttempts) {
            options.backoff.reset()
            emit(EVENT_RECONNECT_FAILED)
            isReconnecting = false
        } else {
            val delay = options.backoff.duration
            isReconnecting = true

            val job = lpScope.launch {
                delay(delay)
                if (shouldSkipReconnect) {
                    return@launch
                }

                emit(EVENT_RECONNECT_ATTEMPT, options.backoff.attempts)

                // Double check
                if (shouldSkipReconnect) {
                    return@launch
                }

                open {
                    isReconnecting = false
                    if (it.isEmpty()) {
                        emit(EVENT_RECONNECT, options.backoff.reset())
                    } else {
                        reconnect()
                        emit(EVENT_RECONNECT_ERROR, it)
                    }
                }
            }

            eventHandles.add { job.cancel() }
        }
    }

    internal fun socket(namespace: String, auth: Map<String, String>): Socket {
        return namespaceSockets.getOrElse(namespace) {
            val sock = Socket(namespace, this, logger, auth)
            namespaceSockets[namespace] = sock
            sock
        }
    }

    internal fun packets(packets: List<EngineIOPacket<*>>) {
        engine?.send(packets)
    }

    internal fun destroy() {
        if (namespaceSockets.values.none { it.active() }) {
            close()
        }
    }

    companion object {
        const val EVENT_OPEN = Engine.EVENT_OPEN

        const val EVENT_CLOSE = Engine.EVENT_CLOSE

        const val EVENT_PACKET = Engine.EVENT_PACKET
        const val EVENT_ERROR = Engine.EVENT_ERROR

        const val EVENT_RECONNECT = "reconnect"

        const val EVENT_RECONNECT_ERROR = "reconnect_error"
        const val EVENT_RECONNECT_FAILED = "reconnect_failed"
        const val EVENT_RECONNECT_ATTEMPT = "reconnect_attempt"

        const val EVENT_TRANSPORT = Engine.EVENT_TRANSPORT
    }
}
