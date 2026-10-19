package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isInstanceOf
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ListingContent
import com.lerchenflo.relisto.marketplace.data.ListingPublicationEntity
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.domain.SyncState
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.testutil.InMemoryImageStorage
import com.lerchenflo.relisto.testutil.inMemoryDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test

/** Republish flow against a fake willhaben that answers like the web client expects. */
class WillhabenRepublisherTest {

    private val database = inMemoryDatabase()
    private val time = FakeTimeProvider(now = 1_000L)
    private val imageStorage = InMemoryImageStorage()
    private val listings = RoomListingRepository(
        database.listingDao(), database.listingImageDao(), database.publicationDao(), imageStorage, time,
    )
    private val fields = RoomWillhabenFieldsDataSource(database.publicationDao())

    private val calls = mutableListOf<String>()
    private val bodies = mutableMapOf<String, String>()
    private var withPayment = false
    private var withPresaveLink = true
    private var copyStatus = HttpStatusCode.OK
    private var motorActivateStatus = HttpStatusCode.OK
    /** Motor drafts offer packages like the real ones: free "Basic", paid "Turbo". */
    private var withPackages = false
    private var onlyPaidPackages = false
    private var selectedPackage: Int? = null

    private fun packages(): String {
        fun product(id: Int, price: String) = """{"productId":$id,"price":$price,"selected":${selectedPackage == id},"label":"x"}"""
        val groups = buildList {
            if (!onlyPaidPackages) add("""{"title":"Basic","isBundle":true,"upsellingProducts":[${product(40050, "0.00")}]}""")
            add("""{"title":"Turbo","isBundle":true,"upsellingProducts":[${product(40051, "9.99")}]}""")
        }
        return """"upsellingProductList":{"requireExactlyOneUpselling":true,"upsellingProducts":[${groups.joinToString(",")}]},"""
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private fun MockRequestHandleScope.json(body: String): HttpResponseData = respond(body, HttpStatusCode.OK, jsonHeaders)

    private val engine = MockEngine { request ->
        val call = "${request.method.value} ${request.url.encodedPath}"
        calls += call
        (request.body as? OutgoingContent.ByteArrayContent)?.let { bodies[call] = it.bytes().decodeToString() }
        when (call) {
            "GET /webapi/iad/productgroups" -> json(
                """{"productGroupList":[
                   {"adTypeId":67,"productContextLinkList":{"productContextLink":[{"adTypeId":67,"price":0.00,"id":"67"}]}},
                   {"adTypeId":21,"productContextLinkList":{"productContextLink":[{"adTypeId":21,"price":0.00,"id":"40021"}]}},
                   {"adTypeId":1,"productContextLinkList":{"productContextLink":[{"adTypeId":1,"price":49.00,"id":"1001"}]}}]}"""
            )
            "POST /webapi/iad/motor/copy/111" -> json(
                """{"heading":"Alt","price":900.0,"adId":null,"productId":2443,"vehicleMileage":12000,
                   "contextLinkList":{"contextLink":[{"id":"presave","serviceName":"iad","relativePath":"/motor/presave"}]}}"""
            )
            "POST /webapi/iad/motor/presave" -> json(
                """{"adId":333,"adUuid":"uuid-333","contextLinkList":{"contextLink":[
                   {"id":"selfLinkFinish","serviceName":"iad","relativePath":"/motor/finish"}]}}"""
            )
            "POST /webapi/iad/motor/finish" -> json("{}")
            "GET /webapi/iad/motor/get/333" -> json(
                """{"heading":"Beta RR 50","price":800.0,"adId":333,"adUuid":"uuid-333",${if (withPackages) packages() else ""}
                   "contextLinkList":{"contextLink":[
                   {"id":"selfLinkFinish","serviceName":"iad","relativePath":"/motor/finish"},
                   {"id":"upsellings","serviceName":"iad","relativePath":"/upselling/new/333"},
                   {"id":"selfLinkActivate","serviceName":"iad","relativePath":"/motor/activate/333"}]}}"""
            )
            "PUT /webapi/iad/upselling/new/333" -> {
                selectedPackage = 40050
                json("{}")
            }
            "PUT /webapi/iad/motor/activate/333" ->
                if (motorActivateStatus != HttpStatusCode.OK) respondError(motorActivateStatus) else respond("", HttpStatusCode.OK)
            "POST /webapi/iad/bap/copy/111" ->
                if (copyStatus != HttpStatusCode.OK) respondError(copyStatus)
                else json(
                    """{"heading":"Alt","description":"Alt","price":200.0,"adId":null,"productId":null,"phoneNo":"x",
                       "treeAttributes":[],"contextLinkList":{"contextLink":[
                       ${if (withPresaveLink) """{"id":"presave","serviceName":"iad","relativePath":"/bap/presave"}""" else ""}]}}"""
                )
            "POST /webapi/iad/bap/presave" -> json(
                """{"adId":222,"adUuid":"uuid-222","productId":67,"contextLinkList":{"contextLink":[
                   {"id":"selfLinkFinish","serviceName":"iad","relativePath":"/bap/finish"},
                   {"id":"imageList","serviceName":"iad","relativePath":"/atimage/222"}]}}"""
            )
            "POST /webapi/iad-upload/atimage/temp" -> json("""{"reference":"temp-${calls.size}"}""")
            "PUT /webapi/iad-upload/atimage/222/order-and-associate" -> json("""{"advertImage":[]}""")
            "POST /webapi/iad/bap/finish" -> json("{}")
            "GET /webapi/iad/bap/222" -> json(
                """{"heading":"Rasentraktor Kart","description":"Gut erhalten","price":180.0,"postCode":"6850","adId":222,
                   "adUuid":"uuid-222","endDate":"2026-12-02T10:00:00.000+00:00","treeAttributes":[],
                   "contextLinkList":{"contextLink":[
                   {"id":"selfLinkActivate","serviceName":"iad","relativePath":"/bap/activate/222"}
                   ${if (withPayment) """,{"id":"paymentURI","serviceName":"iad","relativePath":"/payment/x"}""" else ""}]}}"""
            )
            "PUT /webapi/iad/bap/activate/222" -> respond("", HttpStatusCode.OK)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private val client = HttpClientFactory.createWillhabenClient(engine)
    private val runner = WillhabenRequestRunner(renewSession = { false }, backoff = {})
    private val republisher = WillhabenRepublisher(
        writeDataSource = KtorWillhabenWriteDataSource(client),
        listingsDataSource = KtorWillhabenListingsDataSource(client),
        catalogDataSource = KtorWillhabenCatalogDataSource(client),
        runner = runner,
        listingRepository = listings,
        fieldsDataSource = fields,
        publicationDao = database.publicationDao(),
        imageStorage = imageStorage,
        timeProvider = time,
    )

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun publishedListing(remoteId: String? = "111", adTypeId: Int? = null, status: String = "Abgelaufen"): Long {
        val id = (listings.createListing(ListingContent(title = "Rasentraktor Kart", description = "Gut erhalten", priceCents = 18000, postcode = "6850")) as Result.Success).data
        database.publicationDao().insert(
            ListingPublicationEntity(
                listingId = id, marketplace = Marketplace.WILLHABEN.name,
                marketplaceFieldsJson = adTypeId?.let { """{"adTypeId":$it}""" },
                remoteId = remoteId, remoteUuid = "uuid-111", remoteUrl = null, remoteStatus = status,
                expiresAt = 5L, remoteSnapshotJson = "{}", remoteSnapshotHash = "old", syncState = SyncState.SYNCED.name,
                lastSyncedAt = 1L, lastError = null,
            )
        )
        return id
    }

    @Test
    fun expiredListingIsCopiedFinishedAndActivatedWithLocalContent() = runTest {
        val id = publishedListing()

        val result = republisher.republish(id)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(calls).containsExactly(
            "GET /webapi/iad/productgroups",
            "POST /webapi/iad/bap/copy/111",
            "POST /webapi/iad/bap/presave",
            "POST /webapi/iad/bap/finish",
            "GET /webapi/iad/bap/222",
            "PUT /webapi/iad/bap/activate/222",
        )
        val finishBody = bodies.getValue("POST /webapi/iad/bap/finish")
        assertThat(finishBody).contains("\"heading\":\"Rasentraktor Kart\"")
        assertThat(finishBody).contains("\"price\":180.0")
        assertThat(finishBody).contains("\"adId\":222")
        assertThat(finishBody).contains("\"productId\":67")
    }

    @Test
    fun publicationPointsToTheNewListingAfterwards() = runTest {
        val id = publishedListing()

        republisher.republish(id)

        val publication = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!
        assertThat(publication.remoteId).isEqualTo("222")
        assertThat(publication.remoteUuid).isEqualTo("uuid-222")
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
        assertThat(publication.expiresAt).isEqualTo(1796205600000L)
        assertThat(publication.replacedRemoteIds).isEqualTo("111")
    }

    @Test
    fun earlierReplacedListingsAreKept() = runTest {
        val id = publishedListing()
        val publicationDao = database.publicationDao()
        publicationDao.update(publicationDao.getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!.copy(replacedRemoteIds = "100"))

        republisher.republish(id)

        assertThat(publicationDao.getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!.replacedRemoteIds).isEqualTo("100,111")
    }

    @Test
    fun localImagesAreUploadedAndOrdered() = runTest {
        val id = publishedListing()
        listings.addImageBytes(id, listOf("a".encodeToByteArray() to "jpg", "b".encodeToByteArray() to "jpg"))

        republisher.republish(id)

        assertThat(calls.filter { it.contains("atimage") }).containsExactly(
            "POST /webapi/iad-upload/atimage/temp",
            "POST /webapi/iad-upload/atimage/temp",
            "PUT /webapi/iad-upload/atimage/222/order-and-associate",
        )
        assertThat(bodies.getValue("PUT /webapi/iad-upload/atimage/222/order-and-associate"))
            .isEqualTo("""{"advertImage":[{"reference":"temp-4"},{"reference":"temp-5"}],"floorPlans":[]}""")
    }

    @Test
    fun paidProductStopsBeforeActivation() = runTest {
        withPayment = true
        val id = publishedListing()

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.PaidProduct))
        assertThat(calls).doesNotContain("PUT /webapi/iad/bap/activate/222")
    }

    @Test
    fun missingPresaveLinkMeansApiChanged() = runTest {
        withPresaveLink = false
        val id = publishedListing()

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.MissingLink("presave")))
    }

    @Test
    fun listingWithoutWillhabenListingCannotBeRenewed() = runTest {
        val id = publishedListing(remoteId = null)

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.NotPublished))
        assertThat(calls.isEmpty()).isEqualTo(true)
    }

    @Test
    fun motorListingIsRenewedOverTheMotorRoutesWithTheFreeProduct() = runTest {
        val id = publishedListing(adTypeId = 21)

        val result = republisher.republish(id)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(calls).containsExactly(
            "GET /webapi/iad/productgroups",
            "POST /webapi/iad/motor/copy/111",
            "POST /webapi/iad/motor/presave",
            "POST /webapi/iad/motor/finish",
            "GET /webapi/iad/motor/get/333",
            "PUT /webapi/iad/motor/activate/333",
        )
        val finishBody = bodies.getValue("POST /webapi/iad/motor/finish")
        // The old product of the copy is replaced like the web client does
        assertThat(finishBody).contains("\"productId\":40021")
        assertThat(finishBody).contains("\"heading\":\"Rasentraktor Kart\"")
        // Keys the motor JSON does not have are not added
        assertThat(finishBody).doesNotContain("treeAttributes")
        assertThat(finishBody).doesNotContain("postCode")
        assertThat(database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!.remoteId).isEqualTo("333")
    }

    @Test
    fun freePackageIsChosenBeforeActivating() = runTest {
        withPackages = true
        val id = publishedListing(adTypeId = 21)

        val result = republisher.republish(id)

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(calls.dropWhile { it != "POST /webapi/iad/motor/finish" }).containsExactly(
            "POST /webapi/iad/motor/finish",
            "GET /webapi/iad/motor/get/333",
            "PUT /webapi/iad/upselling/new/333",
            "GET /webapi/iad/motor/get/333",
            "PUT /webapi/iad/motor/activate/333",
        )
        // Exactly one package selected, the free one, like the web client's "Jetzt veröffentlichen"
        val body = Json.parseToJsonElement(bodies.getValue("PUT /webapi/iad/upselling/new/333")).jsonObject
        val selectedById = body.getValue("upsellingProducts").jsonArray
            .flatMap { it.jsonObject.getValue("upsellingProducts").jsonArray }
            .associate { it.jsonObject.getValue("productId").jsonPrimitive.int to it.jsonObject.getValue("selected").jsonPrimitive.boolean }
        assertThat(selectedById).isEqualTo(mapOf(40050 to true, 40051 to false))
    }

    @Test
    fun alreadySelectedFreePackageIsNotSentAgain() = runTest {
        withPackages = true
        selectedPackage = 40050
        val id = publishedListing(adTypeId = 21)

        assertThat(republisher.republish(id)).isEqualTo(Result.Success(Unit))
        assertThat(calls).doesNotContain("PUT /webapi/iad/upselling/new/333")
    }

    @Test
    fun onlyPaidPackagesStopBeforeActivating() = runTest {
        withPackages = true
        onlyPaidPackages = true
        val id = publishedListing(adTypeId = 21)

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.PaidProduct))
        assertThat(calls).doesNotContain("PUT /webapi/iad/upselling/new/333")
        assertThat(calls).doesNotContain("PUT /webapi/iad/motor/activate/333")
    }

    @Test
    fun afterTheCopyThePublicationFollowsTheDraftLikeWillhabenDoes() = runTest {
        // willhaben replaces the expired listing by the draft; a failed activation leaves the draft
        motorActivateStatus = HttpStatusCode.BadRequest
        val id = publishedListing(adTypeId = 21)

        assertThat(republisher.republish(id)).isInstanceOf(Result.Error::class)

        val publication = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!
        assertThat(publication.remoteId).isEqualTo("333")
        assertThat(publication.remoteStatus).isEqualTo("Bearbeitung")
        assertThat(publication.replacedRemoteIds).isEqualTo("111")
        // Following the draft is no local change
        assertThat(publication.syncState).isEqualTo(SyncState.SYNCED.name)
    }

    @Test
    fun draftIsFinishedWithoutCopyingAgain() = runTest {
        motorActivateStatus = HttpStatusCode.BadRequest
        val id = publishedListing(adTypeId = 21)
        republisher.republish(id)
        motorActivateStatus = HttpStatusCode.OK
        calls.clear()

        assertThat(republisher.republish(id)).isEqualTo(Result.Success(Unit))

        assertThat(calls).doesNotContain("POST /webapi/iad/motor/copy/111")
        assertThat(calls).doesNotContain("POST /webapi/iad/motor/presave")
        assertThat(calls.first { it != "GET /webapi/iad/productgroups" }).isEqualTo("GET /webapi/iad/motor/get/333")
        assertThat(calls.last()).isEqualTo("PUT /webapi/iad/motor/activate/333")
        val publication = database.publicationDao().getByListingAndMarketplace(id, Marketplace.WILLHABEN.name)!!
        assertThat(publication.remoteId).isEqualTo("333")
        assertThat(publication.remoteStatus).isNull()
        assertThat(publication.replacedRemoteIds).isEqualTo("111")
    }

    @Test
    fun draftThatIsGoneCannotBeFinished() = runTest {
        // Deleted on willhaben in the meantime; the expired listing it replaced is gone too
        val id = publishedListing(remoteId = "999", adTypeId = 21, status = "Bearbeitung")

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.NotPublished))
        assertThat(calls).doesNotContain("POST /webapi/iad/motor/copy/999")
    }

    @Test
    fun adTypeWithoutFreeProductIsRefusedBeforeAnyWrite() = runTest {
        val id = publishedListing(adTypeId = 1)

        assertThat(republisher.republish(id)).isEqualTo(Result.Error(PublishError.PaidProduct))
        assertThat(calls).containsExactly("GET /webapi/iad/productgroups")
    }

    @Test
    fun failedStepIsReported() = runTest {
        copyStatus = HttpStatusCode.Unauthorized
        val id = publishedListing()

        assertThat(republisher.republish(id))
            .isEqualTo(Result.Error(PublishError.Failed(PublishStep.COPY, DataError.Network.UNAUTHORIZED)))
    }

}
