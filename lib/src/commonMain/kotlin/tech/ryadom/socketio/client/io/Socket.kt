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

import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.hildan.socketio.EngineIOPacket
import org.hildan.socketio.PayloadElement
import org.hildan.socketio.SocketIOPacket
import tech.ryadom.socketio.client.engine.Engine
import tech.ryadom.socketio.client.engine.State
import tech.ryadom.socketio.client.util.Emitter
import tech.ryadom.socketio.client.util.KioLogger
import tech.ryadom.socketio.client.util.On

/**
 * Represents a Socket.IO connection to a specific namespace.
 *
 * This class handles the low-level details of the Socket.IO protocol, including:
 * - Opening and closing the connection.
 * - Sending and receiving events.
 * - Managing acknowledgements (acks).
 * - Handling binary data.
 * - Buffering messages when the connection is not yet established.
 *
 * Users typically interact with this class indirectly through the `IO` object,
 * which provides a higher-level API for creating and managing sockets.
 *
 * @property namespace The namespace this socket is connected to.
 * @property manager The [SocketManager] responsible for the underlying transport.
 * @property logger The [KioLogger] instance for logging.
 * @property auth A map of authentication data to be sent during connection.
 */
class Socket(
    val namespace: String,
    private val manager: SocketManager,
    private val logger: KioLogger,
    private val auth: Map<String, String>
) : Emitter() {

    private var connected = false
    private val eventHandlers = ArrayList<On.Handle>()
    private val ack = mutableMapOf<Int, Ack>()
    private var ackId = 0

    private val sendBuffer = arrayListOf<EngineIOPacket<*>>()
    private val recvBuffer = arrayListOf<ArrayList<Any>>()
    private var reconstructor: BinaryPacketReconstructor? = null

    private var sessionId = ""

    fun open() {
        lpScope.launch {
            logger.info { "open: connected $connected, io reconnecting ${manager.isReconnecting}" }
            if (connected || manager.isReconnecting) {
                return@launch
            }

            subEvents()
            manager.open()
            if (manager.state == State.OPEN) {
                onOpen()
            }
        }
    }

    fun close() {
        lpScope.launch {
            if (connected) {
                manager.packets(listOf(EngineIOPacket.Message(SocketIOPacket.Disconnect(namespace))))
            }

            destroy()

            if (connected) {
                onClose("io client disconnect")
            }
        }
    }

    fun send(vararg args: Any): Socket {
        return emit(EVENT_MESSAGE, *args) as Socket
    }

    override fun emit(event: String, vararg args: Any): Emitter {
        if (RESERVED_EVENTS.contains(event)) {
            onError("emit reserved event: $event")
            return this
        }

        lpScope.launch {
            if (args.isNotEmpty() && args.last() is Ack) {
                val arr = Array(args.size - 1) { args[it] }
                emitWithAck(event, arr, args.last() as Ack)
            } else {
                emitWithAck(event, args, null)
            }
        }

        return this
    }

    private fun emitWithAck(event: String, args: Array<out Any>, ack: Ack?) {
        val ackId = if (ack != null) this.ackId else null
        if (ack != null && ackId != null) {
            if (ack is AckWithTimeout) {
                ack.schedule(lpScope) {
                    this.ack.remove(ackId)

                    val iterator = sendBuffer.iterator()
                    while (iterator.hasNext()) {
                        val pktAckId = when (val pkt = iterator.next()) {
                            is EngineIOPacket.Message<*> -> {
                                when (val payload = pkt.payload) {
                                    is SocketIOPacket.Event -> payload.ackId
                                    is SocketIOPacket.BinaryAck -> payload.ackId
                                    else -> null
                                }
                            }

                            else -> null
                        }
                        if (pktAckId == ackId) {
                            iterator.remove()
                            break
                        }
                    }
                }
            }

            this.ack[ackId] = ack
            this.ackId++
        }

        val packets = if (args.hasBinary()) {
            binaryPackets(args) { payloads, nAttachments ->
                SocketIOPacket.BinaryEvent(
                    namespace = namespace,
                    ackId = ackId,
                    payload = buildList {
                        add(
                            PayloadElement.Json(
                                JsonPrimitive(event)
                            )
                        )
                        addAll(payloads)
                    },
                    nBinaryAttachments = nAttachments
                )
            }
        } else {
            listOf(
                EngineIOPacket.Message(
                    payload = SocketIOPacket.Event(
                        namespace,
                        ackId,
                        buildJsonArray {
                            add(
                                JsonPrimitive(event)
                            )
                            args.forEach { add(toJson(it)) }
                        }
                    )
                )
            )
        }

        if (connected) {
            manager.packets(packets)
        } else {
            sendBuffer.addAll(packets)
        }
    }

    private fun binaryPackets(
        args: Array<out Any>,
        creator: (List<PayloadElement>, Int) -> SocketIOPacket
    ): List<EngineIOPacket<*>> {
        val payloads = ArrayList<PayloadElement>()
        val buffers = ArrayList<ByteString>()
        args.forEach {
            when (it) {
                is JsonElement -> payloads.add(PayloadElement.Json(it))
                is ByteString -> {
                    payloads.add(PayloadElement.AttachmentRef(buffers.size))
                    buffers.add(it)
                }

                else -> payloads.add(PayloadElement.Json(toJson(it)))
            }
        }

        val packets = ArrayList<EngineIOPacket<*>>()
        packets.add(EngineIOPacket.Message(creator(payloads, buffers.size)))
        buffers.forEach {
            packets.add(EngineIOPacket.BinaryData(it))
        }
        return packets
    }

    private fun destroy() {
        eventHandlers.forEach { it.destroy() }
        eventHandlers.clear()
        manager.destroy()
    }

    private fun subEvents() {
        if (eventHandlers.isNotEmpty()) {
            return
        }

        eventHandlers.add(
            On.on(manager, SocketManager.EVENT_OPEN) {
                onOpen()
            }
        )

        eventHandlers.add(
            On.on(manager, SocketManager.EVENT_PACKET) { args ->
                if (args.isNotEmpty()) {
                    when (val pkt = args[0]) {
                        is SocketIOPacket -> onPacket(pkt)
                        is ByteString -> {
                            if (reconstructor == null) {
                                onError("Receive binary buffer while not reconstructing binary packet")
                            }
                            reconstructor?.add(pkt)
                        }
                    }
                }
            }
        )

        eventHandlers.add(
            On.on(manager, SocketManager.EVENT_ERROR) { args ->
                onManagerError(
                    if (args.isNotEmpty() && args[0] is String) args[0] as String else "Manager error"
                )
            }
        )

        eventHandlers.add(
            On.on(manager, SocketManager.EVENT_CLOSE) { args ->
                onClose(if (args.isNotEmpty() && args[0] is String) args[0] as String else "Manager close")
            }
        )
    }

    private fun onOpen() {
        val auth = Json.encodeToJsonElement(auth)
            .takeIf { auth.isNotEmpty() } as? JsonObject

        manager.packets(
            listOf(
                EngineIOPacket.Message(
                    SocketIOPacket.Connect(namespace, auth)
                )
            )
        )
    }

    private fun onPacket(packet: SocketIOPacket) {
        if (namespace != packet.namespace) {
            return
        }

        logger.error { "on packet $packet" }

        when (packet) {
            is SocketIOPacket.Connect -> {
                val sid = packet.payload?.get(Engine.SID)
                if (sid is JsonPrimitive) {
                    onConnect(sid.content)
                }
            }

            is SocketIOPacket.Disconnect -> onDisconnect()
            is SocketIOPacket.ConnectError -> {
                destroy()
                val data = packet.errorData ?: JsonObject(emptyMap())
                logger.error { "Connection error $data" }
                super.emit(EVENT_CONNECT_ERROR, data)
            }

            is SocketIOPacket.Event -> {
                onEvent(packet.ackId, ArrayList(packet.payload))
            }

            is SocketIOPacket.Ack -> onAck(packet.ackId, ArrayList(packet.payload))
            is SocketIOPacket.BinaryEvent,
            is SocketIOPacket.BinaryAck -> {
                if (reconstructor != null) {
                    onError("Receive binary event/ack while reconstructing binary packet, $packet")
                }

                reconstructor = BinaryPacketReconstructor(packet) { isAck, ackId, data ->
                    if (isAck) {
                        onAck(ackId!!, data)
                    } else {
                        onEvent(ackId, data)
                    }

                    reconstructor = null
                }
            }
        }
    }

    private fun onError(msg: String) {
        super.emit(EVENT_ERROR, msg)
    }

    private fun onConnect(id: String) {
        connected = true
        this.sessionId = id

        recvBuffer.forEach { fireEvent(it) }
        recvBuffer.clear()

        if (sendBuffer.isNotEmpty()) {
            manager.packets(sendBuffer)
            sendBuffer.clear()
        }

        super.emit(EVENT_CONNECT)
    }

    private fun onDisconnect() {
        destroy()
        onClose("io server disconnect")
    }

    private fun onEvent(eventId: Int?, data: ArrayList<Any>) {
        if (eventId != null) {
            data.add(createAck(eventId))
        }

        if (data.isEmpty()) {
            return
        }

        if (connected) {
            fireEvent(data)
        } else {
            recvBuffer.add(data)
        }
    }

    private fun fireEvent(data: ArrayList<Any>) {
        val ev = when (val event = data.removeFirst()) {
            is String -> event
            is JsonPrimitive -> event.content
            else -> {
                onError("bad event $event")
                return
            }
        }

        val args = Array(data.size) {
            if (data[it] is JsonElement) {
                (data[it] as JsonElement).flatPrimitive()
            } else {
                data[it]
            }
        }

        super.emit(ev, *args)
    }

    private fun createAck(ackId: Int) = object : Ack {
        private var sent = false

        override fun call(vararg args: Any) {
            lpScope.launch {
                if (sent) {
                    return@launch
                }

                sent = true

                val packets = if (args.hasBinary()) {
                    binaryPackets(args) { payloads, nAttachments ->
                        SocketIOPacket.BinaryAck(namespace, ackId, payloads, nAttachments)
                    }
                } else {
                    listOf(
                        EngineIOPacket.Message(
                            SocketIOPacket.Ack(
                                namespace,
                                ackId,
                                buildJsonArray {
                                    args.forEach { add(toJson(it)) }
                                }
                            )
                        )
                    )
                }

                manager.packets(packets)
            }
        }
    }

    private fun onAck(ackId: Int, data: ArrayList<Any>) {
        val fn = this.ack.remove(ackId)
        if (fn != null) {
            val args = Array(data.size) {
                when (val elem = data[it]) {
                    is JsonElement -> elem.flatPrimitive()
                    else -> elem
                }
            }

            fn.call(*args)
        } else {
            // Logging.info(TAG, "bad ack $ackId")
        }
    }

    private fun onManagerError(error: String) {
        if (!connected) {
            logger.error { "Manager error $error" }
            super.emit(EVENT_CONNECT_ERROR, error)
        }
    }

    private fun onClose(reason: String) {
        connected = false
        sessionId = ""
        super.emit(EVENT_DISCONNECT, reason)
        clearAck()
    }

    private fun clearAck() {
        ack.values.forEach {
            if (it is AckWithTimeout) {
                it.onTimeout()
            }
            // note: basic Ack objects have no way to report an error,
            // so they are simply ignored here
        }
        ack.clear()
    }

    internal fun active() = eventHandlers.isNotEmpty()

    private fun toJson(primitive: Any) = when (primitive) {
        is String -> JsonPrimitive(primitive)
        is Boolean -> JsonPrimitive(primitive)
        is Number -> JsonPrimitive(primitive)
        is JsonElement -> primitive
        else -> JsonPrimitive(primitive.toString())
    }

    companion object {

        const val EVENT_CONNECT = "connect"

        const val EVENT_DISCONNECT = "disconnect"

        const val EVENT_CONNECT_ERROR = "connect_error"

        const val EVENT_MESSAGE = "message"
        const val EVENT_ERROR = SocketManager.EVENT_ERROR

        private val RESERVED_EVENTS = setOf(
            EVENT_CONNECT,
            EVENT_CONNECT_ERROR,
            EVENT_DISCONNECT,
            // used on the server-side
            "disconnecting",
            "newListener",
            "removeListener",
        )
    }
}

private fun JsonElement.flatPrimitive(): Any {
    return when (this) {
        is JsonPrimitive -> {
            return when {
                isString -> content
                this is JsonNull -> "null"
                else -> {
                    val boolVal = jsonPrimitive.booleanOrNull
                    if (boolVal != null) {
                        return boolVal
                    }
                    val intVal = jsonPrimitive.intOrNull
                    if (intVal != null) {
                        return intVal
                    }
                    val longVal = jsonPrimitive.longOrNull
                    if (longVal != null) {
                        return longVal
                    }
                    val floatVal = jsonPrimitive.floatOrNull
                    if (floatVal != null) {
                        return floatVal
                    }
                    val doubleVal = jsonPrimitive.doubleOrNull
                    if (doubleVal != null) {
                        return doubleVal
                    }

                    return 0
                }
            }
        }

        else -> this
    }
}

private fun <T> Array<T>.hasBinary(): Boolean {
    forEach {
        if (it is ByteString) {
            return true
        }
    }

    return false
}
