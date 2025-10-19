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

package tech.ryadom.kio.util

import io.ktor.http.HeadersBuilder

/**
 * Appends a map of headers to a [HeadersBuilder].
 *
 * This utility function iterates over a map where each key is a header name
 * and the value is a list of strings representing one or more values for that header.
 * Each key-value pair from the map is appended to the provided [builder].
 *
 * @param builder The [HeadersBuilder] to which the headers will be added.
 * @param headers A map of header names to a list of their corresponding values.
 */
internal fun putHeaders(
    builder: HeadersBuilder,
    headers: Map<String, List<String>>
) {
    headers.forEach {
        it.value.forEach { v -> builder.append(it.key, v) }
    }
}