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
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal actual fun HttpClient(
    trustAllCerts: Boolean,
    config: HttpClientConfig<*>.() -> Unit
): HttpClient =
    HttpClient(CIO) {
        config(this)
        if (trustAllCerts) {
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(
                            p0: Array<out X509Certificate>?,
                            p1: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            p0: Array<out X509Certificate>?,
                            p1: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                    }
                }
            }
        }
    }
