package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.marketplace.willhaben.domain.RemoteListingSummary
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
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun advert(id: Long, adTypeId: Int, title: String) =
        """{"id":$id,"uuid":"uuid-$id","adTypeId":$adTypeId,"verticalId":5,"description":"$title",
           "advertStatus":{"description":"Aktiv","id":"active","statusId":50},
           "advertImageList":{"advertImage":[{"mainImageUrl":"https://cache.willhaben.at/$id.jpg"},{"mainImageUrl":null}]},
           "advertEditStatusActionsList":{"statusId":50,"statusName":"Online","contextLinkList":{"contextLink":[{"id":"2","serviceName":"iad","relativePath":"/x"}]}}}"""

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
            path.contains("/iad/bap/") || path.contains("/iad/motor/get/") ->
                respond("""{"adId":${path.substringAfterLast('/')},"some":"form"}""", HttpStatusCode.OK, jsonHeaders)
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
            RemoteListingSummary("1", "uuid-1", 67, "Rad", statusText = "Online", imageUrls = listOf("https://cache.willhaben.at/1.jpg")),
            RemoteListingSummary("2", "uuid-2", 21, "Moped", statusText = "Online", imageUrls = listOf("https://cache.willhaben.at/2.jpg")),
        )
        assertThat(requests.single()).isEqualTo("/webapi/myatz/atverz/showpagedadsfromuser?page=1&rows=2&vertical=bap")
    }

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
        )
        assertThat(result.data.first().detailJson).isEqualTo("""{"adId":1,"some":"form"}""")
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
