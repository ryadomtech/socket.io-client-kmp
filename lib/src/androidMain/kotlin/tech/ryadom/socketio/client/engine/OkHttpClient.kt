package tech.ryadom.socketio.client.engine

import android.annotation.SuppressLint
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

actual fun HttpClient(trustAllCerts: Boolean, config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp) {
        config(this)

        if (trustAllCerts) {
            engine {
                this.config {
                    val tal = arrayOf<TrustManager>(
                        @SuppressLint("CustomX509TrustManager")
                        object : X509TrustManager {
                            @SuppressLint("TrustAllX509TrustManager")
                            override fun checkClientTrusted(
                                chain: Array<X509Certificate>,
                                authType: String
                            ) {
                            }

                            @SuppressLint("TrustAllX509TrustManager")
                            override fun checkServerTrusted(
                                chain: Array<X509Certificate>,
                                authType: String
                            ) {
                            }

                            override fun getAcceptedIssuers(): Array<X509Certificate> {
                                return arrayOf()
                            }
                        }
                    )

                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, tal, null)

                    this.hostnameVerifier { _, _ -> true }
                    this.sslSocketFactory(sslContext.socketFactory, tal[0] as X509TrustManager)
                }
            }
        }
    }
