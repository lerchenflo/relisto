package com.lerchenflo.relisto.app.window

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.awt.Rectangle
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

class WindowBoundsStoreTest {

    private val dir = createTempDirectory("window").toFile()
    private val file = File(dir, "window.properties")
    private val store = WindowBoundsStore(file)

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun savedBoundsAreLoadedAgain() {
        val bounds = WindowBounds(x = 40, y = 60, width = 1200, height = 800, maximized = true)

        store.save(bounds)

        assertThat(store.load()).isEqualTo(bounds)
    }

    @Test
    fun nothingSavedOrBrokenFileLoadsNothing() {
        assertThat(store.load()).isNull()

        file.writeText("width=abc\nheight=")
        assertThat(store.load()).isNull()
    }

    @Test
    fun tooSmallSizeIsNotLoaded() {
        store.save(WindowBounds(x = 0, y = 0, width = 10, height = 10, maximized = false))

        assertThat(store.load()).isNull()
    }

    @Test
    fun windowIsVisibleOnlyWhenItsTitleBarIsOnAScreen() {
        val screens = listOf(Rectangle(0, 0, 1920, 1080))

        assertThat(WindowBounds(100, 100, 1200, 800, false).isVisibleOn(screens)).isTrue()
        // Partly off the right edge, title bar still reachable
        assertThat(WindowBounds(1500, 100, 1200, 800, false).isVisibleOn(screens)).isTrue()
        // Was on a second monitor that is gone now
        assertThat(WindowBounds(2500, 100, 1200, 800, false).isVisibleOn(screens)).isFalse()
        assertThat(WindowBounds(100, -900, 1200, 800, false).isVisibleOn(screens)).isFalse()
    }
}
