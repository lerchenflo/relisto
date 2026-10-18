package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.ImportSummary
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.RemoteListingSummary
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
           "advertEditStatusActionsList":{"contextLinkList":{"contextLink":[{"id":"2","serviceName":"iad","relativePath":"/x"}]}}}"""

    private val page1 = """{"totalNumberOfAdvertDetails":3,"verticalList":[{"advertDetails":[${advert(1, 67, "Rad")},${advert(2, 20, "Auto")}]}]}"""
    private val page2 = """{"totalNumberOfAdvertDetails":3,"verticalList":[{"advertDetails":[${advert(3, 67, "Tisch")}]}]}"""

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
            path.contains("/iad/bap/") -> respond("""{"adId":${path.substringAfterLast('/')},"some":"form"}""", HttpStatusCode.OK, jsonHeaders)
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

        assertThat(result.data.total).isEqualTo(3)
        assertThat(result.data.adverts).containsExactly(
            RemoteListingSummary(remoteId = "1", remoteUuid = "uuid-1", adTypeId = 67, title = "Rad"),
            RemoteListingSummary(remoteId = "2", remoteUuid = "uuid-2", adTypeId = 20, title = "Auto"),
        )
        assertThat(requests.single()).isEqualTo("/webapi/myatz/atverz/showpagedadsfromuser?page=1&rows=2&vertical=bap")
    }

    @Test
    fun allPagesAreFetchedAndOnlyMarketplaceListingsKept() = runTest {
        val result = repository.fetchMyMarketplaceListings() as Result.Success

        assertThat(result.data.map { it.summary.remoteId }).containsExactly("1", "3")
        assertThat(requests.filter { it.contains("showpagedadsfromuser") }.size).isEqualTo(2)
    }

    @Test
    fun detailsAreLoadedForEachMarketplaceListing() = runTest {
        val result = repository.fetchMyMarketplaceListings() as Result.Success

        assertThat(requests.filter { it.startsWith("/webapi/iad/bap/") }).containsExactly("/webapi/iad/bap/1", "/webapi/iad/bap/3")
        assertThat(result.data.first().detailJson).isEqualTo("""{"adId":1,"some":"form"}""")
    }

    @Test
    fun expiredSessionIsRenewedDuringImport() = runTest {
        unauthorizedOnce = true

        val result = repository.fetchMyMarketplaceListings()

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

        assertThat(loggedOutRepository.fetchMyMarketplaceListings()).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }

    @Test
    fun importerReportsFoundMarketplaceListings() = runTest {
        val result = FetchingWillhabenImporter(repository).importMyListings()

        assertThat(result).isEqualTo(Result.Success(ImportSummary(found = 2)))
    }
}
