package tech.ryadom.kio.util

import io.ktor.http.HeadersBuilder

internal fun putHeaders(
    builder: HeadersBuilder,
    headers: Map<String, List<String>>
) {
    headers.forEach {
        it.value.forEach { v -> builder.append(it.key, v) }
    }
}