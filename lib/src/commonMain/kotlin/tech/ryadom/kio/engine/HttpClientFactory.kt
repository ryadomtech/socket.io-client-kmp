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

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.websocket.WebSocketSession
import tech.ryadom.kio.LoggingConfig
import tech.ryadom.kio.OptionsConfig
import kotlin.time.Duration.Companion.seconds

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

internal class DefaultHttpClientFactory(
    options: OptionsConfig,
    loggingConfig: LoggingConfig
) : HttpClientFactory {
    private val wsClient = HttpClient(
        trustAllCerts = options.isTrustAllCerts,
    ) {
        if (loggingConfig.isLogKtorRequestsEnabled) {
            val kioLogger = loggingConfig.logger
            install(Logging) {
                this.logger = object : Logger {
                    override fun log(message: String) {
                        kioLogger.info { "Http: $message" }
                    }
                }

                this.level = LogLevel.ALL
            }
        }

        install(WebSockets) {
            pingInterval = 20.seconds
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
