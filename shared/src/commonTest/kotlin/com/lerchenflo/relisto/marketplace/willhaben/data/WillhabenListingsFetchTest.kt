package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActionLink
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** My-adverts list and listing details; shapes follow the willhaben web client's code (bbx-adin). */
class WillhabenListingsFetchTest {

    private val requests = mutableListOf<String>()
    private var unauthorizedOnce = false
    private var renewCalls = 0
    private var imageListStatus = HttpStatusCode.OK
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun advert(id: Long, adTypeId: Int, title: String) =
        """{"id":$id,"uuid":"uuid-$id","adTypeId":$adTypeId,"verticalId":5,"description":"$title",
           "advertStatus":{"description":"Aktiv","id":"active","statusId":50},
           "advertImageList":{"advertImage":[{"mainImageUrl":"https://cache.willhaben.at/$id.jpg"},{"mainImageUrl":null}]},
           "advertEditStatusActionsList":{"statusId":50,"statusName":"Online","contextLinkList":{"contextLink":[
             {"id":"2","serviceName":"myatz","relativePath":"/bap/deactivate/$id"},
             {"id":"requestBillLink","serviceName":"iad","relativePath":"/payment/all/$id"},
             {"id":"3","serviceName":"myatz","relativePath":"/bap/$id"}]}}}"""

    private val page1 = """{"totalNumberOfAdvertDetails":4,"verticalList":[{"advertDetails":[${advert(1, 67, "Rad")},${advert(2, 21, "Moped")}]}]}"""
    private val page2 = """{"totalNumberOfAdvertDetails":4,"verticalList":[{"advertDetails":[${advert(3, 67, "Tisch")},${advert(4, 401, "Job")}]}]}"""

    private val engine = MockEngine { request ->
        requests += request.url.encodedPathAndQuery
        if (unauthorizedOnce) {
            unauthorizedOnce = false
            return@MockEngine respondError(HttpStatusCode.Unauthorized)
        }
        val path = request.url.encodedPath
        when {
            path.endsWith("/myatz/atverz/showpagedadsfromuser") ->
                respond(if (request.url.parameters["page"] == "1") page1 else page2, HttpStatusCode.OK, jsonHeaders)
            path == "/webapi/iad/bap/3" -> respond(
                """{"adId":3,"contextLinkList":{"contextLink":[{"id":"imageList","serviceName":"iad","relativePath":"/atimage/3"}]}}""",
                HttpStatusCode.OK, jsonHeaders,
            )
            path.contains("/iad/bap/") || path.contains("/iad/motor/get/") ->
                respond("""{"adId":${path.substringAfterLast('/')},"some":"form"}""", HttpStatusCode.OK, jsonHeaders)
            // Same image entries as in the my-adverts list (cover image there, all images here)
            path == "/webapi/iad/atimage/3" ->
                if (imageListStatus != HttpStatusCode.OK) respondError(imageListStatus)
                else respond(
                    """{"advertImage":[
                       {"id":1,"mainImageUrl":"https://cache.willhaben.at/3_a_hoved.jpg","referenceImageUrl":"https://cache.willhaben.at/3_a.jpg"},
                       {"id":2,"mainImageUrl":"https://cache.willhaben.at/3_b_hoved.jpg","referenceImageUrl":"https://cache.willhaben.at/3_b.jpg"},
                       {"id":3,"mainImageUrl":"https://cache.willhaben.at/3_c_hoved.jpg","referenceImageUrl":null}]}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private val client = HttpClientFactory.createWillhabenClient(engine)
    private val runner = WillhabenRequestRunner(renewSession = { renewCalls++; true }, backoff = {})
    private val dataSource = KtorWillhabenListingsDataSource(client)
    private val repository = WillhabenListingsFetcher(dataSource, runner, pageSize = 2)

    @Test
    fun myAdvertsPageIsParsed() = runTest {
        val result = dataSource.fetchMyAdverts(page = 1, rows = 2) as Result.Success

        assertThat(result.data.total).isEqualTo(4)
        assertThat(result.data.adverts).containsExactly(
            RemoteListingSummary("1", "uuid-1", 67, "Rad", statusText = "Online", statusActions = actions(1)),
            RemoteListingSummary("2", "uuid-2", 21, "Moped", statusText = "Online", statusActions = actions(2)),
        )
        assertThat(requests.single()).isEqualTo("/webapi/myatz/atverz/showpagedadsfromuser?page=1&rows=2&vertical=bap")
    }

    /** Paid actions (bills, extras) are left out. */
    private fun actions(id: Long) = listOf(
        WillhabenStatusActionLink(WillhabenStatusAction.DEACTIVATE, "myatz", "/bap/deactivate/$id"),
        WillhabenStatusActionLink(WillhabenStatusAction.DELETE, "myatz", "/bap/$id"),
    )

    @Test
    fun allPagesAreFetchedAndListingsOfAllVerticalsKept() = runTest {
        val result = repository.fetchMyListings() as Result.Success

        // Jobs have no edit form the app knows; they are skipped
        assertThat(result.data.map { it.summary.remoteId }).containsExactly("1", "2", "3")
        assertThat(requests.filter { it.contains("showpagedadsfromuser") }.size).isEqualTo(2)
    }

    @Test
    fun detailsAreLoadedFromTheRouteOfEachVertical() = runTest {
        val result = repository.fetchMyListings() as Result.Success

        assertThat(requests.filter { it.startsWith("/webapi/iad/") }).containsExactly(
            "/webapi/iad/bap/1",
            "/webapi/iad/motor/get/2",
            "/webapi/iad/bap/3",
            "/webapi/iad/atimage/3",
        )
        assertThat(result.data.first().detailJson).isEqualTo("""{"adId":1,"some":"form"}""")
    }

    @Test
    fun allImagesComeFromTheImageListInFullSize() = runTest {
        val result = repository.fetchMyListings() as Result.Success

        assertThat(result.data.first { it.summary.remoteId == "3" }.imageUrls).containsExactly(
            "https://cache.willhaben.at/3_a.jpg",
            "https://cache.willhaben.at/3_b.jpg",
            "https://cache.willhaben.at/3_c_hoved.jpg",
        )
        // Without an image list link there are no images
        assertThat(result.data.first { it.summary.remoteId == "1" }.imageUrls).isEmpty()
    }

    @Test
    fun failedImageListGivesNoImagesButKeepsTheListing() = runTest {
        // Only some of the images would let a renew replace the others on willhaben
        imageListStatus = HttpStatusCode.InternalServerError

        val result = repository.fetchMyListings() as Result.Success

        assertThat(result.data.first { it.summary.remoteId == "3" }.imageUrls).isEmpty()
    }

    @Test
    fun expiredSessionIsRenewedDuringImport() = runTest {
        unauthorizedOnce = true

        val result = repository.fetchMyListings()

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(renewCalls).isEqualTo(1)
    }

    @Test
    fun loggedOutImportFails() = runTest {
        val loggedOutRepository = WillhabenListingsFetcher(
            dataSource,
            WillhabenRequestRunner(renewSession = { false }, backoff = {}),
            pageSize = 2,
        )
        unauthorizedOnce = true

        assertThat(loggedOutRepository.fetchMyListings()).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }
}
