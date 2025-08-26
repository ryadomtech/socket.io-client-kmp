package tech.ryadom.socketio.client.util

import io.ktor.http.HeadersBuilder

internal fun putHeaders(
    builder: HeadersBuilder,
    headers: Map<String, List<String>>
) {
    headers.forEach {
        it.value.forEach { v -> builder.append(it.key, v) }
    }
}