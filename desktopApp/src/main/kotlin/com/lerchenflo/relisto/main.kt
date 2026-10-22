package com.lerchenflo.relisto

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() {
    // Texts follow the system language: English systems get values-en, all others German (values/)
    onAppStart()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Relisto",
            state = rememberWindowState(size = DpSize(1400.dp, 900.dp)),
        ) {
            window.minimumSize = Dimension(1000, 650)
            App()
        }
    }
}
