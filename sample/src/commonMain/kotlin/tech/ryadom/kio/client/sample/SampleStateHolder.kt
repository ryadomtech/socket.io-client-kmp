package tech.ryadom.kio.client.sample

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tech.ryadom.kio.kioSocket
import tech.ryadom.kio.util.LogLevel

class SampleStateHolder {

    private val _events = MutableStateFlow(
        listOf<String>()
    )
    val events = _events.map { events ->
        events.map { it.take(100) }
    }

    private val socket by lazy {
        kioSocket(DemoUrl) {
            options {
                isSecure = true
            }

            logging {
                logLevel(LogLevel.INFO)
            }
        }
    }

    fun send(message: String) {
        socket.send(event = "chat message", message)
    }

    fun open() {
        socket.open()
        socket.onAny { content ->
            _events.update { events ->
                events + content.contentToString()
            }
        }
    }

    fun close() {
        socket.close()
    }
}

/**

Please note:

This URL is taken from an [open source](https://github.com/socketio/chat-example?tab=readme-ov-file) for demonstration purposes.

Ryadom Tech is **not** responsible for any information received from this resource or for its functionality.
 */
private const val DemoUrl = "https://stage-socket.technary.net"