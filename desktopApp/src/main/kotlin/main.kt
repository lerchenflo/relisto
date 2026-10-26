package com.lerchenflo.relisto

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lerchenflo.relisto.app.window.WindowBounds
import com.lerchenflo.relisto.app.window.WindowBoundsStore
import com.lerchenflo.relisto.app.window.appIconPainter
import com.lerchenflo.relisto.utilities.DesktopAppDirectories
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.io.File

fun main() {
    // Texts follow the system language: English systems get values-en, all others German (values/)
    onAppStart()
    val boundsStore = WindowBoundsStore(File(DesktopAppDirectories().dataDir, "window.properties"))
    val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
    val saved = boundsStore.load()?.takeIf { it.isVisibleOn(screens) }
    application {
        val state = rememberWindowState(
            placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = saved?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
            size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1400.dp, 900.dp),
        )
        Window(
            onCloseRequest = {
                val position = state.position
                if (position is WindowPosition.Absolute) {
                    boundsStore.save(
                        WindowBounds(
                            x = position.x.value.toInt(),
                            y = position.y.value.toInt(),
                            width = state.size.width.value.toInt(),
                            height = state.size.height.value.toInt(),
                            maximized = state.placement == WindowPlacement.Maximized,
                        )
                    )
                }
                exitApplication()
            },
            title = "Relisto",
            icon = appIconPainter(),
            state = state,
        ) {
            window.minimumSize = Dimension(1000, 650)
            App()
        }
    }
}
