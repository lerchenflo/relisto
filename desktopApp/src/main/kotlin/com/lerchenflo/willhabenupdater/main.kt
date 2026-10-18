package com.lerchenflo.willhabenupdater

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.util.Locale

fun main() {
    // German is the app language; English texts exist in values-en for later
    Locale.setDefault(Locale.GERMAN)
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
