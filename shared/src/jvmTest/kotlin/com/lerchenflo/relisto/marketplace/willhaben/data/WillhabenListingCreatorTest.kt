package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.lerchenflo.relisto.activity.data.RoomActivityLog
import com.lerchenflo.relisto.activity.domain.ActivityAction
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.MissingField
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCategoryRef
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenListingFields
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

/** Publishing a new free private marketplace listing from a local listing. */
class WillhabenListingCreatorTest {

    private val database = inMemoryDatabase()
    private val time = FakeTimeProvider(now = 1_000L)
    private val imageStorage = InMemoryImageStorage()
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), imageStorage, time,
    )
    private val fields = RoomWillhabenFieldsDataSource(database.publicationDao())
    private val activityLog = RoomActivityLog(database.activityLogDao(), database.listingDao(), time)

    private val calls = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()
    private var withPayment = false
    private var activateStatus = HttpStatusCode.OK

    private val engine = MockEngine { request ->
        val query = request.url.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        val call = "${request.method.value} ${request.url.encodedPath}$query"
        calls += call
        (request.body as? OutgoingContent.ByteArrayContent)?.let { bodies[call] = it.bytes().decodeToString() }
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when (call) {
            "GET /webapi/iad/productgroups" -> respond(
                """{"productGroupList":[{"adTypeId":67,"productContextLinkList":{"productContextLink":[{"adTypeId":67,"price":0.00,"id":"67"}]}}]}""",
                HttpStatusCode.OK, json,
            )
            "GET /webapi/iad/bap?productId=67" -> respond(
                """{"heading":null,"description":null,"price":null,"adId":null,"productId":67,"phoneNo":"x",
                   "categoryPath":{"path":[]},"treeAttributes":[],"contextLinkList":{"contextLink":[
                   {"id":"presave","serviceName":"iad","relativePath":"/bap/presave"}]}}""",
                HttpStatusCode.OK, json,
            )
            "POST /webapi/iad/bap/presave" -> respond(
                """{"adId":555,"adUuid":"uuid-555","productId":67,"contextLinkList":{"contextLink":[
                   {"id":"selfLinkFinish","serviceName":"iad","relativePath":"/bap/finish"},
                   {"id":"imageList","serviceName":"iad","relativePath":"/atimage/555"}]}}""",
                HttpStatusCode.OK, json,
            )
            "POST /webapi/iad-upload/atimage/temp" -> respond("""{"reference":"temp-${calls.size}"}""", HttpStatusCode.OK, json)
            "PUT /webapi/iad-upload/atimage/555/order-and-associate" -> respond("""{"advertImage":[]}""", HttpStatusCode.OK, json)
            "POST /webapi/iad/bap/finish" -> respond("{}", HttpStatusCode.OK, json)
            "GET /webapi/iad/bap/555" -> respond(
                """{"heading":"Kinderfahrrad","description":"Gut","price":45.0,"postCode":"6850","adId":555,"adUuid":"uuid-555",
                   "endDate":"2026-12-06T10:00:00.000+00:00","treeAttributes":[],"contextLinkList":{"contextLink":[
                   {"id":"selfLinkFinish","serviceName":"iad","relativePath":"/bap/finish"},
                   {"id":"imageList","serviceName":"iad","relativePath":"/atimage/555"},
                   {"id":"selfLinkActivate","serviceName":"iad","relativePath":"/bap/activate/555"}
                   ${if (withPayment) """,{"id":"paymentURI","serviceName":"iad","relativePath":"/payment/x"}""" else ""}]}}""",
                HttpStatusCode.OK, json,
            )
            "PUT /webapi/iad/bap/activate/555" -> if (activateStatus == HttpStatusCode.OK) respond("", HttpStatusCode.OK) else respondError(activateStatus)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private val client = HttpClientFactory.createWillhabenClient(engine)
    private val runner = WillhabenRequestRunner(renewSession = { false }, backoff = {})
    private val creator = WillhabenListingCreator(
        catalogDataSource = KtorWillhabenCatalogDataSource(client),
        listingsDataSource = KtorWillhabenListingsDataSource(client),
        writeDataSource = KtorWillhabenWriteDataSource(client),
        finisher = WillhabenDraftFinisher(
            KtorWillhabenWriteDataSource(client),
            KtorWillhabenListingsDataSource(client),
            WillhabenImageUploader(KtorWillhabenWriteDataSource(client), imageStorage, runner),
            runner,
        ),
        runner = runner,
        listingRepository = listings,
        fieldsDataSource = fields,
        publicationDao = database.publicationDao(),
        activityLog = activityLog,
        timeProvider = time,
    )

    @AfterTest
    fun tearDown() = database.close()

    private val bicycles = listOf(
        WillhabenCategoryRef(4390, "SPORT_SPORTEQUIPMENT", "Sport / Sportgeräte"),
        WillhabenCategoryRef(4552, "BICYCLE", "Fahrräder"),
    )

    /** A complete local listing: title, category and location are set. */
    private suspend fun localListing(
        categoryPath: List<WillhabenCategoryRef> = bicycles,
        locationId: Int? = 117223,
        title: String = "Kinderfahrrad",
    ): Long {
        val id = (listings.createListing(ListingContent(title = title, description = "Gut", priceCents = 4500, postcode = "6850")) as Result.Success).data
        fields.saveFields(id, WillhabenListingFields(categoryPath = categoryPath, locationId = locationId, locationLabel = "Dornbirn"))
        return id
    }

    private suspend fun publication(id: Long) = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!

    @Test
    fun newListingIsCreatedFromTheTemplateAndActivated() = runTest {
        val id = localListing()

        assertThat(creator.publish(id)).isEqualTo(Result.Success(Unit))

        assertThat(calls).containsExactly(
            "GET /webapi/iad/productgroups",
            "GET /webapi/iad/bap?productId=67",
            "POST /webapi/iad/bap/presave",
            "POST /webapi/iad/bap/finish",
            "GET /webapi/iad/bap/555",
            "PUT /webapi/iad/bap/activate/555",
        )
        val presave = bodies.getValue("POST /webapi/iad/bap/presave")
        assertThat(presave).contains("\"heading\":\"Kinderfahrrad\"")
        assertThat(presave).contains("\"categoryTreeId\":4552")
        assertThat(presave).contains("\"code\":\"BICYCLE\"")
        assertThat(presave).contains("\"locationId\":117223")
        assertThat(presave).contains("\"productId\":67")
        assertThat(presave).contains("\"phoneNo\":\"x\"")
    }

    @Test
    fun publicationIsInSyncWithTheNewListingAfterwards() = runTest {
        val id = localListing()

        creator.publish(id)

        val publication = publication(id)
        assertThat(publication.remoteId).isEqualTo("555")
        assertThat(publication.remoteUuid).isEqualTo("uuid-555")
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication.expiresAt).isEqualTo(1796551200000L)
        assertThat(activityLog.getEntriesAsFlow().first().single().action).isEqualTo(ActivityAction.PUBLISH)
    }

    @Test
    fun imagesAreUploadedBeforeFinishing() = runTest {
        val id = localListing()
        listings.addImageBytes(id, listOf("a".encodeToByteArray() to "jpg"))

        creator.publish(id)

        assertThat(calls.indexOf("PUT /webapi/iad-upload/atimage/555/order-and-associate"))
            .isEqualTo(calls.indexOf("POST /webapi/iad/bap/finish") - 1)
    }

    @Test
    fun stepsAreReportedInOrder() = runTest {
        val id = localListing()
        val steps = mutableListOf<PublishStep>()

        creator.publish(id) { steps += it }

        assertThat(steps).containsExactly(
            PublishStep.PRODUCT, PublishStep.TEMPLATE, PublishStep.PRESAVE, PublishStep.FINISH, PublishStep.RELOAD, PublishStep.ACTIVATE,
        )
    }

    @Test
    fun failedActivationLeavesAnInterruptedPublishThatContinuesWithTheDraft() = runTest {
        activateStatus = HttpStatusCode.BadRequest
        val id = localListing()
        assertThat(creator.publish(id)).isInstanceOf(Result.Error::class)
        assertThat(publication(id).remoteId).isEqualTo("555")
        assertThat(publication(id).syncState).isEqualTo(SyncState.PUBLISHING.name)

        activateStatus = HttpStatusCode.OK
        calls.clear()
        assertThat(creator.publish(id)).isEqualTo(Result.Success(Unit))

        assertThat(calls).doesNotContain("GET /webapi/iad/bap?productId=67")
        assertThat(calls).doesNotContain("POST /webapi/iad/bap/presave")
        assertThat(calls.last()).isEqualTo("PUT /webapi/iad/bap/activate/555")
        assertThat(publication(id).syncState).isEqualTo(SyncState.SYNCED.name)
    }

    @Test
    fun incompleteListingIsRefusedBeforeAnyRequest() = runTest {
        val id = localListing(categoryPath = emptyList(), locationId = null, title = " ")

        assertThat(creator.publish(id))
            .isEqualTo(Result.Error(PublishError.Incomplete(listOf(MissingField.TITLE, MissingField.CATEGORY, MissingField.LOCATION))))
        assertThat(calls).containsExactly()
    }

    @Test
    fun paymentStopsBeforeActivation() = runTest {
        withPayment = true
        val id = localListing()

        assertThat(creator.publish(id)).isEqualTo(Result.Error(PublishError.PaidProduct()))
        assertThat(calls).doesNotContain("PUT /webapi/iad/bap/activate/555")
    }

    @Test
    fun listingThatIsOnWillhabenIsNotPublishedAgain() = runTest {
        val id = localListing()
        creator.publish(id)
        calls.clear()

        assertThat(creator.publish(id)).isEqualTo(Result.Error(PublishError.AlreadyPublished))
        assertThat(calls).containsExactly()
    }
}
