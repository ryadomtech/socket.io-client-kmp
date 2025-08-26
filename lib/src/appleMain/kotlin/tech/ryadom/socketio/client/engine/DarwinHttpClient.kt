package tech.ryadom.socketio.client.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSURLCredential
import platform.Foundation.create
import platform.Foundation.serverTrust

@BetaInteropApi
actual fun HttpClient(trustAllCerts: Boolean, config: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(Darwin) {
        config(this)
        engine {
            if (trustAllCerts) {
                handleChallenge { _, _, challenge, completionHandler ->
                    val serverTrust = challenge.protectionSpace.serverTrust
                    if (serverTrust != null) {
                        val credential = NSURLCredential.create(trust = serverTrust)
                        completionHandler(0, credential)
                    } else {
                        completionHandler(1, null)
                    }
                }
            }
        }
    }
}