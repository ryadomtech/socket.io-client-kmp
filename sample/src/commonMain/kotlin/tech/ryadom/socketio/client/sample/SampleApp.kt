package tech.ryadom.socketio.client.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.diamondedge.logging.logging
import tech.ryadom.socketio.client.io.Options
import tech.ryadom.socketio.client.io.Socket
import tech.ryadom.socketio.client.io.io
import tech.ryadom.socketio.client.util.KioLogger
import tech.ryadom.socketio.client.util.LogLevel

@Composable
fun SampleApp() {
    val stateHolder = remember { SampleStateHolder() }

    stateHolder.kek()

}

class SampleStateHolder {

    private var socket: Socket? = null

    fun kek() {
        Socket.io(
            "https://stage-socket.technary.net",
            options = Options().apply {
                isSecure = true
            },
            logger = object : KioLogger {
                override fun log(
                    level: LogLevel,
                    message: String,
                    cause: Throwable?
                ) {
                    logging("KIO [$level]").info { message }
                    cause?.printStackTrace()
                }

            }
        ) {

            socket = this
            open()
        }
    }
}