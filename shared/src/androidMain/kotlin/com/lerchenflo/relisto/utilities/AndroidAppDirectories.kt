package com.lerchenflo.relisto.utilities

import android.content.Context
import java.io.File

/** App-private folders on Android. */
class AndroidAppDirectories(context: Context) : AppDirectories {
    override val dataDir: String = context.filesDir.absolutePath
    override val imagesDir: String = File(context.filesDir, "images").apply { mkdirs() }.absolutePath
    override val cacheDir: String = context.cacheDir.absolutePath
}
