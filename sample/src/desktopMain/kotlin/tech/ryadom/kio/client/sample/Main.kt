package tech.ryadom.kio.client.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import tech.ryadom.kio.client.sample.ui.SampleApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SocketIOSample",
    ) {
        SampleApp()
    }
}
