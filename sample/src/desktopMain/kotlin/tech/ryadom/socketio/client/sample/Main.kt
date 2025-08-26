package tech.ryadom.socketio.client.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tech.ryadom.socketio.client.sample.SampleApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SocketIOSample",
    ) {
        SampleApp()
    }
}
