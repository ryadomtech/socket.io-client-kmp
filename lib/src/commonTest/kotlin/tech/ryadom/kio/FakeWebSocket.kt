package tech.ryadom.kio

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import tech.ryadom.kio.engine.HttpClientFactory
import kotlin.coroutines.CoroutineContext

/**
 * An in-memory [WebSocketSession] a test can drive from the "server" side.
 */
@Suppress("OVERRIDE_DEPRECATION")
class FakeWebSocketSession : WebSocketSession {

    override val coroutineContext: CoroutineContext = Job() + Dispatchers.Default

    private val incomingFrames = Channel<Frame>(Channel.UNLIMITED)
    private val outgoingFrames = Channel<Frame>(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = incomingFrames
    override val outgoing: SendChannel<Frame> = outgoingFrames
    override val extensions: List<WebSocketExtension<*>> = emptyList()

    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    var isClosedByClient: Boolean = false
        private set

    override suspend fun send(frame: Frame) {
        if (frame is Frame.Close) {
            isClosedByClient = true
            incomingFrames.close()
            return
        }

        outgoingFrames.send(frame)
    }

    override suspend fun flush() = Unit

    override fun terminate() {
        incomingFrames.close()
    }

    /** Pushes a text frame towards the client. */
    fun serverSend(text: String) {
        incomingFrames.trySend(Frame.Text(text))
    }

    /** Pushes a binary frame towards the client. */
    fun serverSend(bytes: ByteArray) {
        incomingFrames.trySend(Frame.Binary(true, bytes))
    }

    /** Drops the connection the way an unreachable server would. */
    fun serverDisconnect() {
        incomingFrames.close()
    }

    /** Awaits the next text frame written by the client. */
    suspend fun nextText(): String = (outgoingFrames.receive() as Frame.Text).readText()

    /** Awaits the next binary frame written by the client. */
    suspend fun nextBytes(): ByteArray = (outgoingFrames.receive() as Frame.Binary).readBytes()
}

/**
 * An [HttpClientFactory] handing out [FakeWebSocketSession]s instead of real connections.
 */
class FakeHttpClientFactory : HttpClientFactory {

    private val sessions = Channel<FakeWebSocketSession>(Channel.UNLIMITED)

    val requestedUrls = mutableListOf<String>()

    private val firstSession = CompletableDeferred<FakeWebSocketSession>()

    override suspend fun createWs(
        url: String,
        request: HttpRequestBuilder.() -> Unit,
        block: suspend WebSocketSession.() -> Unit
    ) {
        val session = FakeWebSocketSession()
        requestedUrls += url
        sessions.send(session)
        firstSession.complete(session)
        session.block()
    }

    override suspend fun httpRequest(
        url: String,
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse = error("Polling transport is not expected in this test")

    /** Awaits the next session the client opened. */
    suspend fun nextSession(): FakeWebSocketSession = sessions.receive()
}

/**
 * The Engine.IO handshake frame, as a server would send it.
 */
fun handshakeFrame(
    sid: String = "engine-sid",
    upgrades: List<String> = emptyList(),
    pingInterval: Int = 25_000,
    pingTimeout: Int = 20_000
): String = """0{"sid":"$sid","upgrades":${
    upgrades.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
},"pingInterval":$pingInterval,"pingTimeout":$pingTimeout}"""
