package tech.ryadom.socketio.client.sample

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import tech.ryadom.socketio.client.sample.SampleApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        SampleApp()
    }
}