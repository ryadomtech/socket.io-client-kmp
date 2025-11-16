package tech.ryadom.kio.client.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.ryadom.kio.client.sample.SampleStateHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleApp() {
    val stateHolder = remember { SampleStateHolder() }
    DisposableEffect(stateHolder) {
        stateHolder.open()
        onDispose {
            stateHolder.close()
        }
    }

    var messageToSend by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = {
                    Text(text = "Events")
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(4.dp)
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    modifier = Modifier.weight(1f),
                    value = messageToSend,
                    onValueChange = { messageToSend = it }
                )

                TextButton(
                    onClick = {
                        stateHolder.send(messageToSend)
                        messageToSend = ""
                    }
                ) {
                    Text(text = "Send")
                }
            }
        }
    ) { paddingValues ->
        val events by stateHolder.events.collectAsState(
            listOf()
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .padding(paddingValues)
        ) {
            items(events) { event -> Text(text = event) }
        }
    }
}