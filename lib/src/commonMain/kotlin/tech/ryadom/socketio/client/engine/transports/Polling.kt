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

package tech.ryadom.socketio.client.engine.transports

import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import io.ktor.util.toMap
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.launch
import org.hildan.socketio.EngineIO
import org.hildan.socketio.EngineIOPacket
import org.hildan.socketio.SocketIO
import org.hildan.socketio.SocketIOPacket
import tech.ryadom.socketio.client.engine.DefaultHttpClientFactory
import tech.ryadom.socketio.client.engine.HttpClientFactory
import tech.ryadom.socketio.client.engine.State
import tech.ryadom.socketio.client.engine.putHeaders
import tech.ryadom.socketio.client.io.lpScope
import tech.ryadom.socketio.client.util.KioLogger

open class Polling(
    options: Options,
    logger: KioLogger,
    rawMessage: Boolean,
    private val httpClientFactory: HttpClientFactory = DefaultHttpClientFactory(options, logger),
) : Transport(NAME, options, rawMessage, logger) {

    private var isPolling = false

    override fun pause(onPause: () -> Unit) {
        state = State.PAUSED
        val paused = {
            state = State.PAUSED
            onPause()
        }

        when {
            isPolling || !isWritable -> {
                val eventsToWait = mutableListOf<String>()
                if (isPolling) eventsToWait += EventPollComplete
                if (!isWritable) eventsToWait += EVENT_DRAIN

                var counter = eventsToWait.size
                eventsToWait.forEach { event ->
                    once(event) {
                        if (--counter == 0) paused()
                    }
                }
            }

            else -> paused()
        }
    }

    override fun doOpen() = poll()

    private fun poll() {
        isPolling = true
        val headers = prepareRequestHeaders(HttpMethod.Get)
        ioScope.launch {
            doRequest(
                uri = uri(),
                method = HttpMethod.Get,
                requestHeaders = headers,
                onResponse = ::onPollComplete
            )
        }

        emit(EventPoll)
    }

    private fun prepareRequestHeaders(method: HttpMethod): Map<String, List<String>> {
        return mutableMapOf<String, List<String>>().apply {
            putAll(options.extraHeaders)
            if (method == HttpMethod.Post) {
                this[HttpHeaders.ContentType] = listOf(
                    ContentType.Text.Plain.withCharset(
                        Charsets.UTF_8
                    )
                ).map { it.contentType }
            }

            this[HttpHeaders.Accept] = listOf(ContentType.Any.contentType)
            emit(EVENT_REQUEST_HEADERS, this)
        }
    }

    private suspend fun doRequest(
        uri: String,
        method: HttpMethod,
        requestHeaders: Map<String, List<String>>,
        data: String? = null,
        onResponse: (String) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) {
        val response = try {
            httpClientFactory.httpRequest(uri) {
                this.method = method
                headers { putHeaders(this, requestHeaders) }
                data?.let { setBody(it) }
            }
        } catch (e: Exception) {
            lpScope.launch { onError("Http exception: ${e.message}") }
            return
        }

        lpScope.launch {
            emit(EVENT_RESPONSE_HEADERS, response.headers.toMap())
        }

        when {
            response.status.isSuccess() -> {
                val body = response.bodyAsText()
                lpScope.launch {
                    onResponse(body)
                    onSuccess()
                }
            }

            else -> lpScope.launch { onError("HTTP exception status: ${response.status}") }
        }
    }

    private fun onPollComplete(data: String) {
        val packets = try {
            if (rawMessage) EngineIO.decodeHttpBatch(data) { it }
            else EngineIO.decodeHttpBatch(data, SocketIO::decode)
        } catch (e: Exception) {
            onError("onPollComplete decoding error: ${e.message}")
            return
        }

        packets.forEach { pkt ->
            when {
                (state == State.OPENING || state == State.CLOSING) && pkt is EngineIOPacket.Open -> onOpen()
                pkt is EngineIOPacket.Close -> {
                    onClose()
                    return@forEach
                }

                else -> onPacket(pkt)
            }
        }

        if (state != State.CLOSED) {
            isPolling = false
            emit(EventPollComplete)
            when (state) {
                State.OPEN -> poll()
                else -> logger.info { "onPollComplete ignore poll, state $state" }
            }
        }
    }

    override fun doSend(packets: List<EngineIOPacket<*>>) {
        isWritable = false
        val data = if (rawMessage) EngineIO.encodeHttpBatch(packets) { it.toString() }
        else EngineIO.encodeHttpBatch(packets) { SocketIO.encode(it as SocketIOPacket) }

        val headers = prepareRequestHeaders(HttpMethod.Post)
        ioScope.launch {
            doRequest(uri(), HttpMethod.Post, headers, data) {
                isWritable = true
                emit(EVENT_DRAIN, packets.size)
            }
        }
    }

    override fun doClose(fromOpenState: Boolean) {
        val doCloseAction: () -> Unit = {
            once(EVENT_DRAIN) { onClose() }
            doSend(listOf(EngineIOPacket.Close))
        }

        when {
            fromOpenState -> {
                doCloseAction()
            }

            else -> {
                once(EVENT_OPEN) { doCloseAction() }
            }
        }
    }

    protected open fun uri() = uri(SecureSchema, InsecureSchema)

    companion object {
        const val NAME = "polling"
    }
}

private const val SecureSchema = "https"
private const val InsecureSchema = "http"

private const val EventPoll = "poll"
private const val EventPollComplete = "pollComplete"