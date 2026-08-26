package tech.ryadom.kio

import org.hildan.socketio.EngineIOPacket
import tech.ryadom.kio.engine.transports.Transport
import tech.ryadom.kio.engine.transports.TransportOptions
import tech.ryadom.kio.util.KioLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val SilentLogger = KioLogger { _, _, _ -> }

private class TestTransport(
    options: TransportOptions
) : Transport("test", options, rawMessage = false, logger = SilentLogger) {

    var openCalls = 0
    var closeCalls = 0
    var closedFromOpenState: Boolean? = null
    val sent = mutableListOf<EngineIOPacket<*>>()

    override fun pause(onPause: () -> Unit) = onPause()

    override fun doOpen() {
        openCalls++
    }

    override fun doSend(packets: List<EngineIOPacket<*>>) {
        sent += packets
    }

    override fun doClose(fromOpenState: Boolean) {
        closeCalls++
        closedFromOpenState = fromOpenState
    }

    fun url() = uri("https", "http")

    fun markOpen() = onOpen()

    fun markClosed() = onClose()

    fun fail(message: String) = onError(message)
}

class TransportTest {

    private fun options(block: TransportOptions.() -> Unit = {}) = TransportOptions().apply {
        hostname = "example.com"
        path = "/socket.io/"
        block()
    }

    @Test
    fun `uri uses the insecure schema by default`() {
        val transport = TestTransport(options { port = 3000 })
        assertEquals("http://example.com:3000/socket.io/", transport.url())
    }

    @Test
    fun `uri uses the secure schema when secure`() {
        val transport = TestTransport(
            options {
                isSecure = true
                port = 8443
            }
        )

        assertEquals("https://example.com:8443/socket.io/", transport.url())
    }

    @Test
    fun `uri omits the default port of the schema`() {
        assertEquals(
            "http://example.com/socket.io/",
            TestTransport(options { port = 80 }).url()
        )

        assertEquals(
            "https://example.com/socket.io/",
            TestTransport(
                options {
                    isSecure = true
                    port = 443
                }
            ).url()
        )
    }

    @Test
    fun `uri omits a non positive port`() {
        assertEquals(
            "http://example.com/socket.io/",
            TestTransport(options { port = -1 }).url()
        )
    }

    @Test
    fun `uri brackets an ipv6 host`() {
        val transport = TestTransport(
            options {
                hostname = "::1"
                port = 3000
            }
        )

        assertEquals("http://[::1]:3000/socket.io/", transport.url())
    }

    @Test
    fun `uri appends the encoded query`() {
        val transport = TestTransport(
            options {
                port = 3000
                query = linkedMapOf("EIO" to "4", "transport" to "polling", "a b" to "c&d")
            }
        )

        assertEquals(
            "http://example.com:3000/socket.io/?EIO=4&transport=polling&a%20b=c%26d",
            transport.url()
        )
    }

    @Test
    fun `uri adds a timestamp when requested`() {
        val transport = TestTransport(
            options {
                port = 3000
                isTimestampRequests = true
                timestampParam = "ts"
            }
        )

        val url = transport.url()
        assertTrue(url.contains("ts="), "expected a timestamp param in $url")
    }

    @Test
    fun `uri does not mutate the configured query`() {
        val query = mutableMapOf("EIO" to "4")
        val transport = TestTransport(
            options {
                this.query = query
                isTimestampRequests = true
            }
        )

        transport.url()

        assertEquals(mapOf("EIO" to "4"), query)
    }

    @Test
    fun `open is only performed once`() {
        val transport = TestTransport(options())

        transport.open()
        transport.open()

        assertEquals(1, transport.openCalls)
    }

    @Test
    fun `open becomes writable only after the transport reports it is open`() {
        val transport = TestTransport(options())

        transport.open()
        assertFalse(transport.isWritable)

        transport.markOpen()
        assertTrue(transport.isWritable)
    }

    @Test
    fun `send is rejected while the transport is not open`() {
        val transport = TestTransport(options())
        transport.open()

        assertFailsWith<IllegalStateException> {
            transport.send(listOf(EngineIOPacket.Ping(null)))
        }
    }

    @Test
    fun `send is delegated once the transport is open`() {
        val transport = TestTransport(options())
        transport.open()
        transport.markOpen()

        transport.send(listOf(EngineIOPacket.Ping(null)))

        assertEquals<List<EngineIOPacket<*>>>(listOf(EngineIOPacket.Ping(null)), transport.sent)
    }

    @Test
    fun `close reports whether the transport was open`() {
        val opened = TestTransport(options())
        opened.open()
        opened.markOpen()
        opened.close()
        assertEquals(true, opened.closedFromOpenState)

        val opening = TestTransport(options())
        opening.open()
        opening.close()
        assertEquals(false, opening.closedFromOpenState)
    }

    @Test
    fun `close is ignored for a transport that never opened`() {
        val transport = TestTransport(options())
        transport.close()
        assertEquals(0, transport.closeCalls)
    }

    @Test
    fun `a closed transport is no longer writable`() {
        val transport = TestTransport(options())
        transport.open()
        transport.markOpen()
        assertTrue(transport.isWritable)

        transport.markClosed()

        assertFalse(transport.isWritable)
        assertFailsWith<IllegalStateException> {
            transport.send(listOf(EngineIOPacket.Ping(null)))
        }
    }

    @Test
    fun `errors are emitted to listeners`() {
        val transport = TestTransport(options())
        val errors = mutableListOf<String>()

        transport.on(Transport.EVENT_ERROR) { errors += it.first().toString() }
        transport.fail("boom")

        assertEquals(listOf("boom"), errors)
    }

    @Test
    fun `open and close are emitted to listeners`() {
        val transport = TestTransport(options())
        val events = mutableListOf<String>()

        transport.on(Transport.EVENT_OPEN) { events += "open" }
        transport.on(Transport.EVENT_CLOSE) { events += "close" }

        transport.open()
        transport.markOpen()
        transport.close()
        transport.markClosed()

        assertEquals(listOf("open", "close"), events)
    }
}
