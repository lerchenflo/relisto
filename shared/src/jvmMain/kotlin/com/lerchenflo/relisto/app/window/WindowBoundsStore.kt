package com.lerchenflo.relisto.app.window

import java.awt.Rectangle
import java.io.File
import java.util.Properties

/** Position and size of the main window in screen pixels. */
data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean,
) {
    /** The title bar must be on a screen, otherwise the window could not be moved (e.g. a second monitor is gone). */
    fun isVisibleOn(screens: List<Rectangle>): Boolean {
        val titleBar = Rectangle(x, y, width, TITLE_BAR_HEIGHT)
        return screens.any { it.intersects(titleBar) }
    }

    private companion object {
        const val TITLE_BAR_HEIGHT = 30
    }
}

/** Remembers the main window between starts in a small properties file. */
class WindowBoundsStore(private val file: File) {

    fun load(): WindowBounds? = runCatching {
        if (!file.exists()) return null
        val props = Properties().apply { file.reader().use(::load) }
        WindowBounds(
            x = props.getProperty(X).toInt(),
            y = props.getProperty(Y).toInt(),
            width = props.getProperty(WIDTH).toInt(),
            height = props.getProperty(HEIGHT).toInt(),
            maximized = props.getProperty(MAXIMIZED).toBoolean(),
        ).takeIf { it.width >= MIN_SIZE && it.height >= MIN_SIZE }
    }.getOrNull()

    fun save(bounds: WindowBounds) {
        runCatching {
            val props = Properties().apply {
                setProperty(X, bounds.x.toString())
                setProperty(Y, bounds.y.toString())
                setProperty(WIDTH, bounds.width.toString())
                setProperty(HEIGHT, bounds.height.toString())
                setProperty(MAXIMIZED, bounds.maximized.toString())
            }
            file.writer().use { props.store(it, null) }
        }
    }

    private companion object {
        const val X = "x"
        const val Y = "y"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val MAXIMIZED = "maximized"

        /** Smaller sizes come from a broken file. */
        const val MIN_SIZE = 200
    }
}
