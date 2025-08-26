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

package tech.ryadom.socketio.client.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HeadersBuilder
import io.ktor.websocket.WebSocketSession
import tech.ryadom.socketio.client.engine.transports.Polling
import tech.ryadom.socketio.client.engine.transports.Transport
import tech.ryadom.socketio.client.engine.transports.WebSocket
import tech.ryadom.socketio.client.util.KioLogger

expect fun HttpClient(
    trustAllCerts: Boolean = false,
    config: HttpClientConfig<*>.() -> Unit = {}
): HttpClient

internal fun putHeaders(
    builder: HeadersBuilder,
    headers: Map<String, List<String>>
) {
    headers.forEach {
        it.value.forEach { v -> builder.append(it.key, v) }
    }
}

interface TransportFactory {
    fun create(
        name: String,
        options: Transport.Options,
        logger: KioLogger,
        rawMessage: Boolean,
    ): Transport
}

object DefaultTransportFactory : TransportFactory {
    override fun create(
        name: String,
        options: Transport.Options,
        logger: KioLogger,
        rawMessage: Boolean,
    ) = when (name) {
        WebSocket.NAME -> WebSocket(options, logger, rawMessage = rawMessage)
        Polling.NAME -> Polling(options, logger, rawMessage = rawMessage)
        else -> throw IllegalArgumentException("Illegal transport name: $name")
    }
}

interface HttpClientFactory {
    suspend fun createWs(
        url: String,
        request: HttpRequestBuilder.() -> Unit,
        block: suspend WebSocketSession.() -> Unit,
    )

    suspend fun httpRequest(
        url: String,
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse
}

class DefaultHttpClientFactory(
    options: Transport.Options,
    logger: KioLogger
) : HttpClientFactory {
    private val wsClient = HttpClient(
        trustAllCerts = options.isTrustAllCerts,
    ) {
        install(Logging) {
            this.logger = object : io.ktor.client.plugins.logging.Logger {
                override fun log(message: String) {
                    logger.info { "OkHttp: $message" }
                }
            }

            this.level = LogLevel.ALL
        }

        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    private val httpClient: HttpClient = wsClient

    override suspend fun createWs(
        url: String,
        request: HttpRequestBuilder.() -> Unit,
        block: suspend WebSocketSession.() -> Unit,
    ) = wsClient.webSocket(url, request, block)

    override suspend fun httpRequest(
        url: String,
        block: HttpRequestBuilder.() -> Unit
    ) = httpClient.request(url, block)
}
