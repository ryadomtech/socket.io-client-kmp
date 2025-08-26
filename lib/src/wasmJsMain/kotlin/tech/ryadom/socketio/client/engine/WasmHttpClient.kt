package tech.ryadom.socketio.client.engine

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js

actual fun HttpClient(trustAllCerts: Boolean, config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Js) {
        config(this)
    }
