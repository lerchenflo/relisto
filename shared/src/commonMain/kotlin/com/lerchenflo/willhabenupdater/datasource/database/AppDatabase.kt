package com.lerchenflo.willhabenupdater.datasource.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.lerchenflo.willhabenupdater.listings.data.ListingEntity
import com.lerchenflo.willhabenupdater.listings.data.ListingImageEntity
import com.lerchenflo.willhabenupdater.marketplace.data.ListingPublicationEntity

@Database(
    entities = [ListingEntity::class, ListingImageEntity::class, ListingPublicationEntity::class],
    exportSchema = true,
    version = 1
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listingDao(): ListingDao
    abstract fun listingImageDao(): ListingImageDao
    abstract fun publicationDao(): PublicationDao

    companion object {
        const val DB_NAME = "willhabenupdater.db"
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
