package com.lerchenflo.willhabenupdater

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Willhabenupdater",
    ) {
        App()
    }
}