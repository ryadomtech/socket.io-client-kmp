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

package tech.ryadom.socketio.client.util

/**
 * Utility object for parsing and encoding query strings.
 *
 * This object provides methods to:
 * - Encode a map of key-value pairs into a URL-encoded query string.
 * - Decode a URL-encoded query string into a map of key-value pairs.
 *
 * It utilizes [UriUtils] for URL encoding and decoding of individual keys and values.
 */
object QSParsingUtils {

    /**
     * Encode a map of key-value pairs into a query string.
     *
     * @param obj The map to encode.
     * @return The encoded query string.
     */
    fun encode(obj: Map<String, String>): String {
        return obj.entries.joinToString("&") { (k, v) ->
            "${UriUtils.encode(k)}=${UriUtils.encode(v)}"
        }
    }

    /**
     * Decodes a query string into a map of key-value pairs.
     *
     * @param qs The query string to decode.
     * @return A map representing the decoded query string.
     *         Returns an empty map if the input string is empty.
     *         Keys and values are URI-decoded.
     *         If a key is empty, the key-value pair is ignored.
     *         If a value is empty after the '=', it's treated as an empty string.
     */
    fun decode(qs: String): Map<String, String> {
        if (qs.isEmpty()) {
            return mapOf()
        }

        return qs.splitToSequence('&')
            .mapNotNull {
                val (key, value) = it.split('=', limit = 2)
                if (key.isEmpty()) {
                    return@mapNotNull null
                }

                val decodedValue = value.takeIf { v -> v.isNotEmpty() }
                    ?.let { v -> UriUtils.decode(v) }
                    ?: ""

                UriUtils.decode(key) to decodedValue
            }
            .toMap()
    }
}