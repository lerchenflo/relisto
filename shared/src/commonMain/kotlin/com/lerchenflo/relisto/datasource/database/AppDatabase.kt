package com.lerchenflo.relisto.datasource.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.lerchenflo.relisto.activity.data.ActivityLogEntity
import com.lerchenflo.relisto.listings.data.ListingEntity
import com.lerchenflo.relisto.listings.data.ListingImageEntity
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity

@Database(
    entities = [ListingEntity::class, ListingImageEntity::class, ListingPublicationEntity::class, ActivityLogEntity::class],
    exportSchema = true,
    version = 4,
    // Never destructive: every version step keeps the user's data
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // listing_publications.replacedRemoteIds
        AutoMigration(from = 2, to = 3), // listing_publications.remoteActionsJson
        AutoMigration(from = 3, to = 4), // listing_publications.syncedImagesHash, activity_log
    ],
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listingDao(): ListingDao
    abstract fun listingImageDao(): ListingImageDao
    abstract fun publicationDao(): PublicationDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        const val DB_NAME = "relisto.db"
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
