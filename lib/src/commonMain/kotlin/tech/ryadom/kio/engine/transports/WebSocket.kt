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

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.headers
import io.ktor.util.toMap
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.unsafe.UnsafeByteStringApi
import kotlinx.io.bytestring.unsafe.UnsafeByteStringOperations
import org.hildan.socketio.EngineIO
import org.hildan.socketio.EngineIOPacket
import org.hildan.socketio.InvalidEngineIOPacketException
import org.hildan.socketio.SocketIOPacket
import tech.ryadom.kio.engine.HttpClientFactory
import tech.ryadom.kio.engine.State
import tech.ryadom.kio.lpScope
import tech.ryadom.kio.util.KioLogger
import tech.ryadom.kio.util.putHeaders

/**
 * Represents a WebSocket transport layer for Engine.IO communication.
 *
 * This class handles the establishment and management of a WebSocket connection
 * for sending and receiving Engine.IO packets. It extends the [Transport] class
 * and implements the specific logic for WebSocket communication.
 *
 * @property options The configuration options for the transport.
 * @property logger The logger instance for logging messages.
 * @property rawMessage A boolean indicating whether messages should be treated as raw Engine.IO packets
 *                      or as Socket.IO packets.
 * @property httpClientFactory A factory for creating HTTP clients, used for establishing the WebSocket connection.
 *                             Defaults to [DefaultHttpClientFactory].
 */
internal open class WebSocket(
    options: TransportOptions,
    logger: KioLogger,
    rawMessage: Boolean,
    private val httpClientFactory: HttpClientFactory,
) : Transport(NAME, options, rawMessage, logger) {

    private var socketSession: WebSocketSession? = null

    override fun pause(onPause: () -> Unit) = Unit /* no-op */

    override fun doOpen() {
        val requestHeaders = mutableMapOf<String, List<String>>().apply {
            putAll(options.extraHeaders)
            emit(EVENT_REQUEST_HEADERS, this)
        }

        ioScope.launch {
            try {
                httpClientFactory.createWs(
                    url = uri(),
                    request = { headers { putHeaders(this, requestHeaders) } }
                ) {
                    socketSession = this
                    if (this is DefaultClientWebSocketSession) {
                        val respHeaders = call.response.headers.toMap()
                        lpScope.launch {
                            emit(EVENT_RESPONSE_HEADERS, respHeaders)
                            onOpen()
                        }
                    }

                    listen()
                }
            } catch (e: Exception) {
                lpScope.launch { onError("Ws exception: ${e.message}") }
            }
        }
    }

    protected open fun uri() = uri(SecureSchema, InsecureSchema)

    private suspend fun listen() {
        while (true) {
            try {
                val frame = socketSession?.incoming?.receive() ?: break
                when (frame) {
                    is Frame.Text -> onWsText(frame.readText())
                    is Frame.Binary -> onWsBinary(frame.readBytes())
                    is Frame.Close -> {
                        break
                    }

                    else -> {}
                }
            } catch (e: Exception) {
                logger.error(e) { "Error while reading ws frame" }
                break
            }
        }

        lpScope.launch { onClose() }
    }

    private fun onWsText(data: String) = lpScope.launch {
        logger.warn { "[WebSocket] On WS text" }
        val packet = try {
            if (rawMessage) EngineIO.decodeWsFrame(data) { it }
            else EngineIO.decodeSocketIO(data)
        } catch (e: InvalidEngineIOPacketException) {
            onError("onWsText decode error: ${e.message}")
            return@launch
        }

        onPacket(packet)
    }

    @OptIn(UnsafeByteStringApi::class)
    private fun onWsBinary(data: ByteArray) = lpScope.launch {
        logger.warn { "[WebSocket] On WS binary" }
        onPacket(
            EngineIO.decodeWsFrame(
                UnsafeByteStringOperations.wrapUnsafe(data)
            )
        )
    }

    @OptIn(UnsafeByteStringApi::class)
    override fun doSend(packets: List<EngineIOPacket<*>>) {
        isWritable = false

        ioScope.launch {
            packets.forEach { pkt ->
                if (state != State.OPEN) return@forEach
                try {
                    when (pkt) {
                        is EngineIOPacket.BinaryData ->
                            UnsafeByteStringOperations.withByteArrayUnsafe(pkt.payload) {
                                socketSession?.send(it)
                            }

                        else -> {
                            val data = if (rawMessage) {
                                EngineIO.encodeWsFrame(pkt) { it.toString() }
                            } else {
                                @Suppress("unchecked_cast")
                                EngineIO.encodeSocketIO(pkt as EngineIOPacket<SocketIOPacket>)
                            }

                            socketSession?.send(data)
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error sending packets $packets" }
                }
            }

            lpScope.launch {
                isWritable = true
                emit(EVENT_DRAIN, packets.size)
            }
        }
    }

    override fun doClose(fromOpenState: Boolean) {
        ioScope.launch { socketSession?.close() }
    }

    companion object {
        const val NAME = "websocket"
    }
}

private const val SecureSchema = "wss"
private const val InsecureSchema = "ws"