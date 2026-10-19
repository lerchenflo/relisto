package com.lerchenflo.relisto.datasource.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.lerchenflo.relisto.listings.data.ListingEntity
import com.lerchenflo.relisto.listings.data.ListingImageEntity
import com.lerchenflo.relisto.listings.data.ListingWithImages
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingDao {

    @Transaction
    @Query("SELECT * FROM listings ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ListingWithImages>

    @Transaction
    @Query("SELECT * FROM listings ORDER BY updatedAt DESC")
    fun getAllAsFlow(): Flow<List<ListingWithImages>>

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    suspend fun getById(id: Long): ListingWithImages?

    @Transaction
    @Query("SELECT * FROM listings WHERE id = :id")
    fun getByIdAsFlow(id: Long): Flow<ListingWithImages?>

    @Insert
    suspend fun insert(listing: ListingEntity): Long

    @Update
    suspend fun update(listing: ListingEntity)

    @Delete
    suspend fun delete(listing: ListingEntity)
}

@Dao
interface ListingImageDao {

    @Query("SELECT * FROM listing_images WHERE id = :id")
    suspend fun getById(id: Long): ListingImageEntity?

    @Query("SELECT * FROM listing_images WHERE id = :id")
    fun getByIdAsFlow(id: Long): Flow<ListingImageEntity?>

    @Query("SELECT * FROM listing_images WHERE listingId = :listingId ORDER BY position")
    suspend fun getByListingId(listingId: Long): List<ListingImageEntity>

    @Query("SELECT * FROM listing_images WHERE listingId = :listingId ORDER BY position")
    fun getByListingIdAsFlow(listingId: Long): Flow<List<ListingImageEntity>>

    @Query("SELECT DISTINCT fileName FROM listing_images")
    suspend fun getAllFileNames(): List<String>

    @Query("SELECT DISTINCT fileName FROM listing_images")
    fun getAllFileNamesAsFlow(): Flow<List<String>>

    @Insert
    suspend fun insert(image: ListingImageEntity): Long

    @Insert
    suspend fun insertAll(images: List<ListingImageEntity>)

    @Update
    suspend fun update(image: ListingImageEntity)

    @Update
    suspend fun updateAll(images: List<ListingImageEntity>)

    @Delete
    suspend fun delete(image: ListingImageEntity)
}

@Dao
interface PublicationDao {

    @Query("SELECT * FROM listing_publications WHERE listingId = :listingId AND marketplace = :marketplace")
    suspend fun getByListingAndMarketplace(listingId: Long, marketplace: String): ListingPublicationEntity?

    @Query("SELECT * FROM listing_publications WHERE listingId = :listingId AND marketplace = :marketplace")
    fun getByListingAndMarketplaceAsFlow(listingId: Long, marketplace: String): Flow<ListingPublicationEntity?>

    @Query("SELECT * FROM listing_publications WHERE marketplace = :marketplace AND remoteId = :remoteId")
    suspend fun getByMarketplaceAndRemoteId(marketplace: String, remoteId: String): ListingPublicationEntity?

    @Query("SELECT * FROM listing_publications WHERE marketplace = :marketplace AND remoteId = :remoteId")
    fun getByMarketplaceAndRemoteIdAsFlow(marketplace: String, remoteId: String): Flow<ListingPublicationEntity?>

    @Query("SELECT * FROM listing_publications")
    suspend fun getAll(): List<ListingPublicationEntity>

    @Query("SELECT * FROM listing_publications")
    fun getAllAsFlow(): Flow<List<ListingPublicationEntity>>

    @Query("SELECT * FROM listing_publications WHERE listingId = :listingId")
    suspend fun getByListingId(listingId: Long): List<ListingPublicationEntity>

    @Query("SELECT * FROM listing_publications WHERE listingId = :listingId")
    fun getByListingIdAsFlow(listingId: Long): Flow<List<ListingPublicationEntity>>

    @Upsert
    suspend fun upsert(publication: ListingPublicationEntity): Long

    @Insert
    suspend fun insert(publication: ListingPublicationEntity): Long

    @Update
    suspend fun update(publication: ListingPublicationEntity)

    @Delete
    suspend fun delete(publication: ListingPublicationEntity)
}
