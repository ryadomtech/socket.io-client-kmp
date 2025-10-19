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

package tech.ryadom.kio.engine

import io.ktor.http.Url
import io.ktor.http.isSecure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.hildan.socketio.EngineIOPacket
import tech.ryadom.kio.engine.transports.Polling
import tech.ryadom.kio.engine.transports.Transport
import tech.ryadom.kio.engine.transports.WebSocket
import tech.ryadom.kio.lpScope
import tech.ryadom.kio.util.Emitter
import tech.ryadom.kio.util.KioLogger
import tech.ryadom.kio.util.On

/**
 * The `Engine` class is responsible for managing the connection to the server,
 * handling transports, and processing Engine.IO packets. It extends [Emitter]
 * to allow for event-based communication.
 *
 * This class orchestrates the lifecycle of the connection, including opening,
 * closing, upgrading transports, and handling heartbeats.
 *
 * @property uri The URI of the Engine.IO server.
 * @property options Configuration options for the Engine.
 * @property logger A [KioLogger] instance for logging.
 * @property rawMessage A boolean indicating whether to process messages as raw binary data.
 *                      Defaults to `false`.
 */
class Engine(
    uri: String,
    internal val options: Options,
    private val logger: KioLogger,
    private val httpClientFactory: HttpClientFactory,
    private val rawMessage: Boolean = false
) : Emitter() {

    /**
     * Configuration options for the Engine.
     *
     * @property transports List of allowed transports in order of preference
     * @property upgrade Whether to allow transport upgrades
     * @property rememberUpgrade Remember successful websocket upgrade for future connections
     * @property transportOptions Transport-specific configuration options
     */
    open class Options : Transport.Options() {
        var transports: List<String> = listOf(Polling.NAME, WebSocket.NAME)
        var upgrade = true
        var rememberUpgrade = false
        var transportOptions: Map<String, Transport.Options> = mapOf()
    }

    // Connection state management
    private var state = State.INIT
    private var upgrades = listOf<String>()
    private var pingInterval = 0
    private var pingTimeout = 0
    private var upgrading = false
    internal var sessionId = ""

    // Transport management
    internal var transport: Transport? = null
    private val transportSubscriptions = ArrayList<On.Handle>()

    // Packet buffering
    internal val writeBuffer = ArrayDeque<EngineIOPacket<*>>()
    private var prevBufferLen = 0

    // Heartbeat management
    private val heartbeatListener = Listener { onHeartBeat() }
    private var pingTimeoutJob: Job? = null

    init {
        configureFromUri(uri)
    }

    private fun configureFromUri(uri: String) {
        val url = Url(uri)
        options.isSecure = url.protocol.isSecure()
        options.hostname = sanitizeHostname(url.host)
        options.port = url.port

        if (!url.parameters.isEmpty()) {
            options.query.putAll(
                url.parameters.entries().associate { it.key to it.value[0] }
            )
        }

        if (options.path.isEmpty()) {
            options.path = "/engine.io/"
        }
    }

    private fun sanitizeHostname(host: String): String {
        if (host.count { it == ':' } > 1) {
            return host.removePrefix("[").removeSuffix("]")
        }

        return host
    }

    fun open() {
        if (state != State.INIT && state != State.CLOSED) return

        val transportName = determineInitialTransport()
        state = State.OPENING
        createTransport(transportName).run {
            setTransport(this)
            open()
        }
    }

    private fun determineInitialTransport(): String {
        return if (options.rememberUpgrade && priorWebsocketSuccess && WebSocket.NAME in options.transports) {
            WebSocket.NAME
        } else {
            options.transports.first()
        }
    }

    fun send(packets: List<EngineIOPacket<*>>) = sendPackets(packets)

    fun close() {
        if (state !in listOf(State.OPENING, State.OPEN)) return
        state = State.CLOSING

        when {
            writeBuffer.isNotEmpty() -> waitForDrainBeforeClose()
            upgrading -> waitForUpgradeCompletion()
            else -> forceClose()
        }
    }

    private fun waitForDrainBeforeClose() {
        once(EVENT_DRAIN) {
            if (upgrading) waitForUpgradeCompletion() else forceClose()
        }
    }

    private fun waitForUpgradeCompletion() {
        val handler = Listener { forceClose() }
        once(EVENT_UPGRADE, handler)
        once(EVENT_UPGRADE_ERROR, handler)
    }

    private fun forceClose() = onClose("force close")

    private fun createTransport(name: String): Transport {
        val query = configureTransportQuery(name)
        val transportOpts = createTransportOptions(name, query)

        val transport = when (name) {
            WebSocket.NAME -> WebSocket(
                options = transportOpts,
                logger = logger,
                rawMessage = rawMessage,
                httpClientFactory = httpClientFactory
            )

            Polling.NAME -> Polling(
                options = transportOpts,
                logger = logger,
                rawMessage = rawMessage,
                httpClientFactory = httpClientFactory
            )

            else -> throw IllegalArgumentException("Illegal transport name: $name")
        }

        emit(EVENT_TRANSPORT, transport)

        return transport
    }

    private fun configureTransportQuery(name: String): MutableMap<String, String> {
        return HashMap<String, String>().apply {
            putAll(options.query)
            put("EIO", "4")
            put("transport", name)
            sessionId.takeIf { it.isNotEmpty() }?.let { put(SID, it) }
        }
    }

    private fun createTransportOptions(
        name: String,
        query: MutableMap<String, String>
    ): Transport.Options {
        val baseOpts = options.transportOptions[name]
        return Transport.Options().apply {
            this.query = query
            isSecure = baseOpts?.isSecure ?: options.isSecure
            hostname = baseOpts?.hostname ?: options.hostname
            port = baseOpts?.port ?: options.port
            path = baseOpts?.path ?: options.path
            isTimestampRequests = baseOpts?.isTimestampRequests ?: options.isTimestampRequests
            timestampParam = baseOpts?.timestampParam ?: options.timestampParam
            extraHeaders = baseOpts?.extraHeaders ?: options.extraHeaders
            isTrustAllCerts = baseOpts?.isTrustAllCerts ?: options.isTrustAllCerts
        }
    }

    private fun setTransport(transport: Transport) {
        clearExistingTransport()
        this.transport = transport
        setupTransportListeners(transport)
    }

    private fun clearExistingTransport() {
        transport?.let {
            transportSubscriptions.forEach { sub -> sub.destroy() }
            transportSubscriptions.clear()
        }
    }

    private fun setupTransportListeners(transport: Transport) {
        transportSubscriptions.addAll(
            listOf(
                On.on(transport, Transport.EVENT_DRAIN) { args ->
                    (args.firstOrNull() as? Int)?.let { onDrain(it) }
                },
                On.on(transport, Transport.EVENT_PACKET) { args ->
                    (args.firstOrNull() as? EngineIOPacket<*>)?.let { onPacket(it) }
                },
                On.on(transport, Transport.EVENT_ERROR) { args ->
                    (args.firstOrNull() as? String)?.let { onError(it) }
                },
                On.on(transport, Transport.EVENT_CLOSE) { onClose("transport close") }
            ))
    }

    private fun onDrain(processedCount: Int) {
        repeat(processedCount) { writeBuffer.removeFirst() }
        prevBufferLen -= processedCount

        when {
            writeBuffer.isEmpty() -> emit(EVENT_DRAIN)
            writeBuffer.size > prevBufferLen -> flush()
        }
    }

    private fun sendPackets(packets: List<EngineIOPacket<*>>) {
        if (state !in listOf(State.OPENING, State.OPEN)) return

        emit(EVENT_PACKET_CREATE, packets.size)
        writeBuffer.addAll(packets)
        flush()
    }

    private fun onPacket(packet: EngineIOPacket<*>) {
        if (inactive()) return

        emit(EVENT_PACKET, packet)
        emit(EVENT_HEARTBEAT)

        when (packet) {
            is EngineIOPacket.Open -> onHandshake(packet)
            is EngineIOPacket.Ping -> handlePingPacket()
            is EngineIOPacket.BinaryData -> emit(EVENT_DATA, packet.payload)
            is EngineIOPacket.Message<*> -> packet.payload?.let { emit(EVENT_DATA, it) }
            else -> Unit
        }
    }

    private fun handlePingPacket() {
        emit(EVENT_PING)
        sendPackets(listOf(EngineIOPacket.Pong(null)))
    }

    private fun onHandshake(pkt: EngineIOPacket.Open) {
        sessionId = pkt.sid
        transport?.options?.query?.set(SID, sessionId)
        upgrades = filterUpgrades(pkt.upgrades)
        pingInterval = pkt.pingInterval
        pingTimeout = pkt.pingTimeout

        emit(EVENT_HANDSHAKE, pkt)
        onOpen()

        if (state == State.OPEN) {
            onHeartBeat()
            off(EVENT_HEARTBEAT, heartbeatListener)
            on(EVENT_HEARTBEAT, heartbeatListener)
        }
    }

    internal fun filterUpgrades(upgrades: List<String>) =
        upgrades.filter { it in options.transports && it != transport?.name }

    private fun onOpen() {
        state = State.OPEN
        priorWebsocketSuccess = transport?.name == WebSocket.NAME
        emit(EVENT_OPEN)

        if (shouldUpgrade()) {
            upgrades.forEach { probe(it) }
        }
    }

    private fun shouldUpgrade() =
        options.upgrade && transport?.name == Polling.NAME && upgrades.isNotEmpty()

    private fun flush() {
        if (canFlush()) {
            val packets = writeBuffer.subList(prevBufferLen, writeBuffer.size)
            prevBufferLen = writeBuffer.size
            transport?.send(ArrayList(packets))
            emit(EVENT_FLUSH)
        }
    }

    private fun canFlush() = state != State.CLOSED &&
            transport?.isWritable == true &&
            !upgrading &&
            writeBuffer.size > prevBufferLen

    private fun probe(name: String) {
        val transport = createTransport(name)
        var failed = false
        priorWebsocketSuccess = false

        val cleanUp = ArrayList<() -> Unit>()
        var cleaned = false

        val onTransportOpen = Listener {
            if (failed) {
                return@Listener
            }

            val ping = EngineIOPacket.Ping(PROBE)
            transport.send(listOf(ping))
            transport.once(Transport.EVENT_PACKET) { args ->
                if (failed) {
                    return@once
                }

                if (args.isNotEmpty() && args[0] is EngineIOPacket.Pong
                    && (args[0] as EngineIOPacket.Pong).payload == PROBE
                ) {
                    upgrading = true
                    emit(EVENT_UPGRADING, transport)
                    if (cleaned) {
                        return@once
                    }

                    priorWebsocketSuccess = transport.name == WebSocket.NAME
                    val currentTransport = this@Engine.transport ?: return@once
                    currentTransport.pause {
                        if (failed || state == State.CLOSED) {
                            return@pause
                        }

                        cleanUp[0]()
                        transport.once(EVENT_DRAIN) {
                            emit(EVENT_UPGRADE, transport)
                            setTransport(transport)
                            cleaned = true
                            upgrading = false
                            flush()
                        }

                        transport.send(listOf(EngineIOPacket.Upgrade))
                    }
                } else {
                    emit(EVENT_UPGRADE_ERROR, PROBE_ERROR)
                }
            }
        }

        val freezeTransport = Listener {
            if (failed) {
                return@Listener
            }

            failed = true
            cleanUp[0]()
            transport.close()
            cleaned = true
        }

        val onTransportError = Listener {
            freezeTransport.call()
            emit(EVENT_UPGRADE_ERROR, PROBE_ERROR)
        }

        val onTransportClose = Listener {
            onTransportError.call("transport closed with")
        }

        val onClose = Listener {
            onTransportError.call("socket closed with")
        }

        val onUpgrade = Listener { args ->
            if (args.isNotEmpty() && args[0] is Transport) {
                val to = args[0] as Transport
                if (to.name != transport.name) {
                    freezeTransport.call()
                }
            }
        }

        cleanUp.add {
            transport.off(Transport.EVENT_OPEN, onTransportOpen)
            transport.off(Transport.EVENT_ERROR, onTransportError)
            transport.off(Transport.EVENT_CLOSE, onTransportClose)
            off(EVENT_CLOSE, onClose)
            off(EVENT_UPGRADING, onUpgrade)
        }

        transport.once(Transport.EVENT_OPEN, onTransportOpen)
        transport.once(Transport.EVENT_ERROR, onTransportError)
        transport.once(Transport.EVENT_CLOSE, onTransportClose)
        once(EVENT_CLOSE, onClose)
        once(EVENT_UPGRADING, onUpgrade)

        transport.open()
    }

    private fun onHeartBeat() {
        pingTimeoutJob?.cancel()
        pingTimeoutJob = lpScope.launch {
            delay((pingInterval + pingTimeout).toLong())
            if (!inactive()) onClose("ping timeout")
        }
    }

    private fun onError(msg: String) {
        priorWebsocketSuccess = false
        emit(EVENT_ERROR, msg)
        onClose(msg)
    }

    private fun onClose(reason: String) {
        if (inactive()) return

        pingTimeoutJob?.cancel()
        transport?.close()
        cleanupResources()

        state = State.CLOSED
        sessionId = ""
        emit(EVENT_CLOSE, reason)
        clearBuffers()
    }

    private fun cleanupResources() {
        transportSubscriptions.forEach { it.destroy() }
        transportSubscriptions.clear()
    }

    private fun clearBuffers() {
        writeBuffer.clear()
        prevBufferLen = 0
    }

    private fun inactive() = state !in setOf(State.OPENING, State.OPEN, State.CLOSING)

    companion object Companion {
        private var priorWebsocketSuccess = false

        internal const val PROBE = "probe"
        internal const val SID = "sid"
        private const val PROBE_ERROR = "probe error"

        // Connection lifecycle events
        const val EVENT_OPEN = "open"
        const val EVENT_CLOSE = "close"
        const val EVENT_ERROR = "error"
        const val EVENT_UPGRADE_ERROR = "upgradeError"
        const val EVENT_FLUSH = "flush"
        const val EVENT_DRAIN = "drain"
        const val EVENT_HANDSHAKE = "handshake"
        const val EVENT_UPGRADING = "upgrading"
        const val EVENT_UPGRADE = "upgrade"

        // Packet events
        const val EVENT_PACKET = "packet"
        const val EVENT_PACKET_CREATE = "packetCreate"
        const val EVENT_HEARTBEAT = "heartbeat"
        const val EVENT_PING = "ping"
        const val EVENT_DATA = "data"

        // Transport events
        const val EVENT_TRANSPORT = "transport"
    }
}