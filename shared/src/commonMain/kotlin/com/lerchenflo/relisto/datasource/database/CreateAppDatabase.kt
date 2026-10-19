package com.lerchenflo.relisto.datasource.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Builds the database from the platform builder. No destructive migration fallback:
 * the local database is the master copy of all listings, schema changes need real migrations.
 */
class CreateAppDatabase(private val builder: RoomDatabase.Builder<AppDatabase>) {

    fun getDatabase(): AppDatabase {
        return builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
}
