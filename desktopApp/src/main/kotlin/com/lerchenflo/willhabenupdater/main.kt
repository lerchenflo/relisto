package com.lerchenflo.willhabenupdater

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() {
    onAppStart()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Willhaben Updater",
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
        ) {
            window.minimumSize = Dimension(1000, 650)
            App()
        }
    }
}
