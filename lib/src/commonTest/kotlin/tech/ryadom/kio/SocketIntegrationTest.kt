package tech.ryadom.kio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.ByteString
import tech.ryadom.kio.engine.transports.WebSocket
import tech.ryadom.kio.io.Ack
import tech.ryadom.kio.io.Socket
import tech.ryadom.kio.io.SocketManager
import tech.ryadom.kio.io.SocketManagerOptions
import tech.ryadom.kio.util.KioLogger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private val TestLogger = KioLogger { _, _, _ -> }

/**
 * Drives a [Socket] through a full Socket.IO session over a [FakeWebSocketSession].
 */
class SocketIntegrationTest {

    private class Harness(
        val factory: FakeHttpClientFactory,
        val manager: SocketManager,
        val socket: Socket
    ) {
        val connects = mutableListOf<Unit>()

        init {
            socket.on(Socket.EVENT_CONNECT) { connects.add(Unit) }
        }
    }

    /**
     * Runs [body] against a fresh socket and tears the manager down afterwards.
     *
     * The teardown matters: a manager left open keeps rescheduling reconnection and heartbeat
     * timers, which on Node keeps the event loop alive and the test process from ever exiting.
     */
    private fun withSocket(
        namespace: String = "/",
        auth: Map<String, String> = emptyMap(),
        configure: SocketManagerOptions.() -> Unit = {},
        body: suspend (Harness) -> Unit
    ) = runTest {
        withContext(Dispatchers.Default) {
            val options = SocketManagerOptions().apply {
                transports = listOf(WebSocket.NAME)
                upgrade = false
                reconnectionDelay = 20.milliseconds
                reconnectionDelayMax = 40.milliseconds
                randomizationFactor = 0.0
                configure()
            }

            val factory = FakeHttpClientFactory()
            val manager = SocketManager("http://localhost:3000", TestLogger, options, factory)
            val harness = Harness(factory, manager, manager.socket(namespace, auth))

            try {
                body(harness)
            } finally {
                harness.socket.off()
                manager.off()
                manager.close()
            }
        }
    }

    private suspend fun awaitUntil(
        message: String,
        timeoutMillis: Long = 10_000,
        condition: () -> Boolean
    ) {
        withTimeout(timeoutMillis) {
            while (!condition()) {
                delay(2)
            }
        }.also { if (!condition()) error(message) }
    }

    /** Brings the socket up to the point where the server accepted the namespace connection. */
    private suspend fun Harness.connect(): FakeWebSocketSession {
        val expected = connects.size + 1
        socket.open()

        val session = factory.nextSession()
        session.serverSend(handshakeFrame())
        assertEquals("40", session.nextText())
        session.serverSend("""40{"sid":"socket-sid"}""")

        awaitUntil("the socket never reported itself as connected") { connects.size >= expected }
        return session
    }

    @Test
    fun `connects and announces the namespace`() = withSocket { h ->
        val connected = mutableListOf<String>()
        h.socket.on(Socket.EVENT_CONNECT) { connected += "connect" }

        h.connect()

        awaitUntil("connect was never emitted") { connected.isNotEmpty() }
        assertTrue(h.factory.requestedUrls.single().startsWith("ws://localhost:3000/socket.io/"))
    }

    @Test
    fun `announces a non default namespace`() = withSocket(namespace = "/admin") { h ->
        h.socket.open()
        val session = h.factory.nextSession()

        session.serverSend(handshakeFrame())

        assertEquals("40/admin,", session.nextText())
    }

    @Test
    fun `sends the auth payload with the connect packet`() = withSocket(auth = mapOf("token" to "secret")) { h ->
        h.socket.open()
        val session = h.factory.nextSession()

        session.serverSend(handshakeFrame())

        assertEquals("""40{"token":"secret"}""", session.nextText())
    }

    @Test
    fun `emits an event to the server`() = withSocket { h ->
        val session = h.connect()

        h.socket.send("chat", "hello")

        assertEquals("""42["chat","hello"]""", session.nextText())
    }

    @Test
    fun `buffers events emitted before the connection is established`() = withSocket { h ->
        h.socket.open()
        val session = h.factory.nextSession()

        h.socket.send("early", "one")
        session.serverSend(handshakeFrame())

        assertEquals("40", session.nextText())
        session.serverSend("""40{"sid":"socket-sid"}""")

        assertEquals("""42["early","one"]""", session.nextText())
    }

    @Test
    fun `receives an event from the server`() = withSocket { h ->
        val session = h.connect()

        val received = mutableListOf<List<Any>>()
        h.socket.on("chat") { received.add(it.toList()) }

        session.serverSend("""42["chat","hi",7,true,1.5]""")

        awaitUntil("event was never received") { received.isNotEmpty() }
        assertEquals(listOf<Any>("hi", 7, true, 1.5), received.single())
    }

    @Test
    fun `buffers events received before the namespace is connected`() = withSocket { h ->
        h.socket.open()
        val session = h.factory.nextSession()

        val received = mutableListOf<List<Any>>()
        h.socket.on("early") { received.add(it.toList()) }

        session.serverSend(handshakeFrame())
        assertEquals("40", session.nextText())

        session.serverSend("""42["early","buffered"]""")
        session.serverSend("""40{"sid":"socket-sid"}""")

        awaitUntil("buffered event was never delivered") { received.isNotEmpty() }
        assertEquals(listOf<Any>("buffered"), received.single())
    }

    @Test
    fun `answers a server side ack`() = withSocket { h ->
        val session = h.connect()

        h.socket.on("need-ack") { args ->
            (args.last() as Ack).call("done", 1)
        }

        session.serverSend("""4212["need-ack","payload"]""")

        assertEquals("""4312["done",1]""", session.nextText())
    }

    @Test
    fun `answers a server side ack only once`() = withSocket { h ->
        val session = h.connect()

        h.socket.on("need-ack") { args ->
            val ack = args.last() as Ack
            ack.call("first")
            ack.call("second")
        }

        session.serverSend("""4212["need-ack"]""")

        assertEquals("""4312["first"]""", session.nextText())

        h.socket.send("marker")
        assertEquals("""42["marker"]""", session.nextText())
    }

    @Test
    fun `resolves a client side ack`() = withSocket { h ->
        val session = h.connect()

        val answers = mutableListOf<List<Any>>()
        h.socket.send("ask", "question", Ack { answers.add(it.toList()) })

        assertEquals("""420["ask","question"]""", session.nextText())

        session.serverSend("""430["answer",42]""")

        awaitUntil("ack was never resolved") { answers.isNotEmpty() }
        assertEquals(listOf<Any>("answer", 42), answers.single())
    }

    @Test
    fun `numbers ack ids incrementally`() = withSocket { h ->
        val session = h.connect()

        h.socket.send("a", Ack { })
        h.socket.send("b", Ack { })

        assertEquals("""420["a"]""", session.nextText())
        assertEquals("""421["b"]""", session.nextText())
    }

    @Test
    fun `receives a binary event`() = withSocket { h ->
        val session = h.connect()

        val received = mutableListOf<List<Any>>()
        h.socket.on("bin") { received.add(it.toList()) }

        session.serverSend("""451-["bin",{"_placeholder":true,"num":0}]""")
        session.serverSend(byteArrayOf(1, 2, 3))

        awaitUntil("binary event was never delivered") { received.isNotEmpty() }

        val payload = received.single().single()
        assertIs<ByteString>(payload)
        assertContentEquals(byteArrayOf(1, 2, 3), payload.toByteArray())
    }

    @Test
    fun `a binary event without attachments does not block the following ones`() = withSocket { h ->
        val session = h.connect()

        val received = mutableListOf<List<Any>>()
        h.socket.on("empty") { received.add(it.toList()) }
        h.socket.on("bin") { received.add(it.toList()) }

        session.serverSend("""450-["empty"]""")
        session.serverSend("""451-["bin",{"_placeholder":true,"num":0}]""")
        session.serverSend(byteArrayOf(9))

        awaitUntil("the second binary event was blocked") { received.size == 2 }
        assertIs<ByteString>(received[1].single())
    }

    @Test
    fun `sends a binary event`() = withSocket { h ->
        val session = h.connect()

        h.socket.send("upload", ByteString(4, 5, 6))

        assertEquals("""451-["upload",{"_placeholder":true,"num":0}]""", session.nextText())
        assertContentEquals(byteArrayOf(4, 5, 6), session.nextBytes())
    }

    @Test
    fun `reports a connection error from the server`() = withSocket { h ->
        h.socket.open()
        val session = h.factory.nextSession()

        val errors = mutableListOf<Any>()
        h.socket.on(Socket.EVENT_CONNECT_ERROR) { errors += it.first() }

        session.serverSend(handshakeFrame())
        assertEquals("40", session.nextText())
        session.serverSend("""44{"message":"Not authorized"}""")

        awaitUntil("connect_error was never emitted") { errors.isNotEmpty() }
    }

    @Test
    fun `reports a server initiated disconnect`() = withSocket { h ->
        val session = h.connect()

        val reasons = mutableListOf<String>()
        h.socket.on(Socket.EVENT_DISCONNECT) { reasons += it.first().toString() }

        session.serverSend("41")

        awaitUntil("disconnect was never emitted") { reasons.isNotEmpty() }
        assertEquals("io server disconnect", reasons.single())
    }

    @Test
    fun `close sends a disconnect packet and reports the reason`() = withSocket { h ->
        val session = h.connect()

        val reasons = mutableListOf<String>()
        h.socket.on(Socket.EVENT_DISCONNECT) { reasons += it.first().toString() }

        h.socket.close()

        assertEquals("41", session.nextText())
        awaitUntil("disconnect was never emitted") { reasons.isNotEmpty() }
        assertEquals("io client disconnect", reasons.single())
    }

    @Test
    fun `ignores packets addressed to another namespace`() = withSocket { h ->
        val session = h.connect()

        val received = mutableListOf<List<Any>>()
        h.socket.on("chat") { received.add(it.toList()) }

        session.serverSend("""42/other,["chat","not mine"]""")
        session.serverSend("""42["chat","mine"]""")

        awaitUntil("event was never received") { received.isNotEmpty() }
        delay(50)

        assertEquals(1, received.size)
        assertEquals(listOf<Any>("mine"), received.single())
    }

    @Test
    fun `refuses to emit a reserved event`() = withSocket { h ->
        h.connect()

        val errors = mutableListOf<Any>()
        h.socket.on(Socket.EVENT_ERROR) { errors += it.first() }

        h.socket.send(Socket.EVENT_CONNECT)

        assertEquals(1, errors.size)
        assertTrue(errors.single().toString().contains(Socket.EVENT_CONNECT))
    }

    @Test
    fun `answers an engine ping with a pong`() = withSocket { h ->
        val session = h.connect()

        session.serverSend("2")

        assertEquals("3", session.nextText())
    }

    @Test
    fun `reconnects after the transport dropped and reports the attempt count`() = withSocket { h ->
        val first = h.connect()

        val reconnects = mutableListOf<Any>()
        val attempts = mutableListOf<Any>()
        h.manager.on(SocketManager.EVENT_RECONNECT) { reconnects += it.first() }
        h.manager.on(SocketManager.EVENT_RECONNECT_ATTEMPT) { attempts += it.first() }

        first.serverDisconnect()

        val second = h.factory.nextSession()
        second.serverSend(handshakeFrame(sid = "engine-sid-2"))
        assertEquals("40", second.nextText())

        awaitUntil("reconnect was never emitted") { reconnects.isNotEmpty() }

        assertIs<Int>(attempts.first())
        assertIs<Int>(reconnects.single())
        assertEquals(1, reconnects.single())
    }

    @Test
    fun `stops reconnecting after the configured number of attempts`() =
        withSocket(configure = { reconnectionAttempts = 2 }) { h ->
            val first = h.connect()

            var failed = false
            h.manager.on(SocketManager.EVENT_RECONNECT_FAILED) { failed = true }

            first.serverDisconnect()

            repeat(2) {
                h.factory.nextSession().serverDisconnect()
            }

            awaitUntil("reconnect_failed was never emitted") { failed }
        }

    @Test
    fun `does not reconnect after an explicit close`() = withSocket { h ->
        val session = h.connect()

        h.socket.close()
        assertEquals("41", session.nextText())

        delay(200)

        assertEquals(1, h.factory.requestedUrls.size)
    }
}
