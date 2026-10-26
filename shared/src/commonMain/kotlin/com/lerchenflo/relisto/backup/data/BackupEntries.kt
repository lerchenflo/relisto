package com.lerchenflo.relisto.backup.data

import com.lerchenflo.relisto.datasource.database.AppDatabase

/** Entry names inside a backup zip. */
object BackupEntries {
    const val DATABASE = AppDatabase.DB_NAME
    const val IMAGES_FOLDER = "images"
    private const val IMAGES_PREFIX = "$IMAGES_FOLDER/"

    fun image(fileName: String) = IMAGES_PREFIX + fileName

    /** Only the database and plain image names are unpacked, so no entry can leave the target folder. */
    fun isAllowed(name: String): Boolean {
        if (name == DATABASE) return true
        val fileName = name.removePrefix(IMAGES_PREFIX)
        return name.startsWith(IMAGES_PREFIX) && fileName.isNotEmpty() &&
            '/' !in fileName && '\\' !in fileName && fileName != "." && fileName != ".."
    }
}
