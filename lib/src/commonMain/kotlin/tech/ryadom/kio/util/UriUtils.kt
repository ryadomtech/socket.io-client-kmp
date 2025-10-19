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

/**
 * Utility object for encoding and decoding URI components.
 *
 * This object provides functions to perform URI encoding and decoding,
 * handling special characters and percent-encoding as per URI specifications.
 */
internal object UriUtils {

    /**
     * Encodes a string for use in a URI.
     *
     * This function iterates through the input string, character by character.
     * If a character is considered "safe" (alphanumeric or one of `-_.!~*'()`),
     * it is appended to the result as is.
     * Otherwise, the character is percent-encoded. This means it's converted
     * to its UTF-8 byte representation, and each byte is then represented
     * as a '%' character followed by two hexadecimal digits.
     *
     * For example, the space character ' ' would be encoded as "%20".
     *
     * @param str The string to be URI-encoded.
     * @return The URI-encoded string.
     */
    fun encode(str: String): String = buildString {
        for (char in str) {
            if (char in SafeChars) {
                append(char)
            } else {
                char.toString().encodeToByteArray()
                    .forEach { byte ->
                        append('%')
                        append(HexDigits[(byte.toInt() shr 4) and 0x0F])
                        append(HexDigits[byte.toInt() and 0x0F])
                    }
            }
        }
    }

    /**
     * Decodes a URI-encoded string.
     *
     * This function takes a string that has been URI-encoded and converts it back
     * to its original form. It handles percent-encoded characters (e.g., %20 for space)
     * and also decodes '+' characters back to spaces.
     *
     * For multi-byte UTF-8 characters, it collects all consecutive percent-encoded
     * bytes and then converts them to the corresponding string.
     *
     * @param str The URI-encoded string to decode.
     * @return The decoded string.
     * @throws IllegalArgumentException if an invalid percent-encoding sequence is encountered
     * (e.g., '%' not followed by two hexadecimal digits).
     */
    fun decode(str: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < str.length) {
            when (val c = str[i]) {
                '%' -> {
                    require(i + 2 < str.length) { "Invalid % encoding at position $i" }

                    val byte = str.substring(i + 1, i + 3)
                        .toInt(16)
                        .toByte()

                    if (byte < 0) {
                        val bytes = arrayListOf<Byte>()
                        bytes.add(byte)

                        while (i + 3 < str.length && str[i + 3] == '%') {
                            i += 3

                            val nextByte = str.substring(i + 1, i + 3)
                                .toInt(16)
                                .toByte()

                            bytes.add(nextByte)
                        }

                        result.append(
                            bytes.toByteArray().toString()
                        )
                    } else {
                        result.append(
                            byte.toInt().toChar()
                        )
                    }

                    i += 2
                }

                '+' -> result.append(' ')

                else -> result.append(c)
            }

            i++
        }

        return result.toString()
    }
}

/**
 * A character array representing the hexadecimal digits (0-9 and A-F).
 * Used for converting byte values to their hexadecimal string representation
 * during URI encoding.
 */
private val HexDigits = "0123456789ABCDEF".toCharArray()

/**
 * A set of characters that are considered safe and do not need to be percent-encoded in a URI.
 * This set includes uppercase and lowercase letters, digits, and the special characters.
 */
private val SafeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    .toCharArray()
    .toSet()