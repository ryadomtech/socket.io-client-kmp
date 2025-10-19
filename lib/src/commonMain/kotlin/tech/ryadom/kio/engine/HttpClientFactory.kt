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

class DefaultHttpClientFactory(
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
