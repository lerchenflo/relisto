package com.lerchenflo.relisto.marketplace.willhaben.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.listings.data.ListingEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class RoomWillhabenFieldsDataSourceTest {

    private val database: AppDatabase = inMemoryDatabase()
    private val publicationDao = database.publicationDao()
    private val dataSource = RoomWillhabenFieldsDataSource(publicationDao)

    private val fields = WillhabenListingFields(
        categoryPath = listOf(
            WillhabenCategoryRef(4390, "SPORT_SPORTEQUIPMENT", "Sport / Sportgeräte"),
            WillhabenCategoryRef(4552, "BICYCLE", "Fahrräder"),
        ),
        attributes = mapOf("COLOR" to listOf("RED", "BLUE")),
        locationId = 117223,
        locationLabel = "Wien, 01. Bezirk, Innere Stadt",
        delivery = DeliveryOptions(pickup = true, shipping = true, shippingPriceCents = 590, paylivery = false),
    )

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun insertListing(): Long = database.listingDao().insert(
        ListingEntity(
            title = "Rad", description = "", priceCents = null, condition = null, postcode = "1010",
            archived = false, createdAt = 0, updatedAt = 0,
        )
    )

    @Test
    fun noFieldsBeforeFirstSave() = runTest {
        val listingId = insertListing()

        assertThat(dataSource.getFields(listingId)).isNull()
    }

    @Test
    fun savedFieldsAreReadBackUnchanged() = runTest {
        val listingId = insertListing()

        dataSource.saveFields(listingId, fields)

        assertThat(dataSource.getFields(listingId)).isEqualTo(fields)
    }

    @Test
    fun firstSaveCreatesLocalOnlyWillhabenPublication() = runTest {
        val listingId = insertListing()

        dataSource.saveFields(listingId, fields)

        val publication = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!
        assertThat(publication.syncState).isEqualTo(SyncState.LOCAL_ONLY.name)
    }

    @Test
    fun changingFieldsOfSyncedPublicationMarksLocalChanges() = runTest {
        val listingId = insertListing()
        dataSource.saveFields(listingId, fields)
        val publication = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!
        publicationDao.update(publication.copy(syncState = SyncState.SYNCED.name))

        dataSource.saveFields(listingId, fields.copy(locationId = 1))

        val updated = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!
        assertThat(updated.syncState).isEqualTo(SyncState.LOCAL_CHANGES.name)
    }

    @Test
    fun savingSameFieldsKeepsSyncedState() = runTest {
        val listingId = insertListing()
        dataSource.saveFields(listingId, fields)
        val publication = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!
        publicationDao.update(publication.copy(syncState = SyncState.SYNCED.name))

        dataSource.saveFields(listingId, fields)

        val updated = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)!!
        assertThat(updated.syncState).isEqualTo(SyncState.SYNCED.name)
    }

    @Test
    fun flowEmitsSavedFields() = runTest {
        val listingId = insertListing()

        dataSource.getFieldsAsFlow(listingId).test {
            assertThat(awaitItem()).isNull()
            dataSource.saveFields(listingId, fields)
            assertThat(awaitItem()).isEqualTo(fields)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
