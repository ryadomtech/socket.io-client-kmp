package tech.ryadom.kio.client.sample

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tech.ryadom.kio.kioSocket
import tech.ryadom.kio.util.LogLevel

data class Event(
    val type: String,
    val content: String
)

class SampleStateHolder {

    private val _events = MutableStateFlow(
        listOf<Event>()
    )
    val events = _events.asStateFlow()

    private val socket by lazy {
        kioSocket("dfdf") {
            options {

            }

            logging {
                logLevel(LogLevel.INFO)
            }
        }
    }

    fun send(message: String) {
        socket.send(message)
    }

    fun open() {
        socket.open()

        socket.onAnyIncoming { content ->
            _events.update { events ->
                events + Event("in", content.contentToString())
            }
        }
    }

    fun close() {
        socket.close()
    }
}