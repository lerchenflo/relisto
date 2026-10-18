package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.app.domain.DataError
import com.lerchenflo.willhabenupdater.app.domain.Result
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttributeReference
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenAttributeValue
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCategoryTree
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenLocation
import com.lerchenflo.willhabenupdater.testutil.FakeTimeProvider
import com.lerchenflo.willhabenupdater.testutil.TempAppDirectories
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class CachedWillhabenCatalogRepositoryTest {

    private val dirs = TempAppDirectories()
    private val time = FakeTimeProvider(now = 1_000_000L)
    private val requests = mutableListOf<String>()
    private var serverUp = true

    private val engine = MockEngine { request ->
        requests += request.url.encodedPath
        if (!serverUp) return@MockEngine respondError(HttpStatusCode.ServiceUnavailable)
        val body = when {
            request.url.encodedPath.contains("categorytree") -> TREE_JSON
            request.url.encodedPath.contains("categorysuggestion") -> SUGGESTION_JSON
            else -> """{"locations":[{"id":117223,"description":"Wien, 01. Bezirk, Innere Stadt"}]}"""
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun repository() = CachedWillhabenCatalogRepository(
        remote = KtorWillhabenCatalogDataSource(HttpClientFactory.createWillhabenClient(engine)),
        appDirectories = dirs,
        timeProvider = time,
    )

    @AfterTest
    fun tearDown() = dirs.delete()

    private fun Result<WillhabenCategoryTree, DataError>.tree(): WillhabenCategoryTree =
        (this as Result.Success).data

    @Test
    fun treeIsParsedWithCategoriesAndAttributes() = runTest {
        val tree = repository().getCategoryTree().tree()

        val comics = tree.root.children.first().children.single()
        assertThat(comics.label).isEqualTo("Comics / Mangas")
        assertThat(comics.attributeReferences).containsExactly(
            WillhabenAttributeReference(code = "Zustand", required = true, multiSelect = false),
            WillhabenAttributeReference(code = "Uebergabe", required = true, multiSelect = true),
        )
        assertThat(tree.attributesByCode.getValue("Zustand").values).containsExactly(
            WillhabenAttributeValue("neu", "Neu"),
            WillhabenAttributeValue("gebraucht", "Gebraucht"),
        )
    }

    @Test
    fun secondCallUsesMemoryWithoutDownload() = runTest {
        val repository = repository()
        repository.getCategoryTree()
        repository.getCategoryTree()

        assertThat(requests.size).isEqualTo(1)
    }

    @Test
    fun freshFileCacheIsUsedByNewInstance() = runTest {
        repository().getCategoryTree()
        time.now += 60_000

        val result = repository().getCategoryTree()

        assertThat(requests.size).isEqualTo(1)
        assertThat(result).isInstanceOf(Result.Success::class)
    }

    @Test
    fun fileCacheOlderThanSevenDaysIsDownloadedAgain() = runTest {
        repository().getCategoryTree()
        time.now += 8L * 24 * 60 * 60 * 1000

        repository().getCategoryTree()

        assertThat(requests.size).isEqualTo(2)
    }

    @Test
    fun staleCacheIsUsedWhenDownloadFails() = runTest {
        repository().getCategoryTree()
        time.now += 8L * 24 * 60 * 60 * 1000
        serverUp = false

        val result = repository().getCategoryTree()

        assertThat(result).isInstanceOf(Result.Success::class)
    }

    @Test
    fun downloadFailureWithoutCacheReturnsNetworkError() = runTest {
        serverUp = false

        val result = repository().getCategoryTree()

        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERVICE_UNAVAILABLE))
    }

    @Test
    fun forceRefreshDownloadsAgain() = runTest {
        val repository = repository()
        repository.getCategoryTree()

        repository.getCategoryTree(forceRefresh = true)

        assertThat(requests.size).isEqualTo(2)
    }

    @Test
    fun leavesContainFullPathOfEveryLeafCategory() = runTest {
        val tree = repository().getCategoryTree().tree()

        assertThat(tree.leaves().map { path -> path.map { it.label } }).containsExactly(
            listOf("Antiquitäten / Kunst", "Comics / Mangas"),
            listOf("Sport / Sportgeräte", "Fahrräder"),
        )
    }

    @Test
    fun pathOfReturnsCategoriesFromTopToGivenNode() = runTest {
        val tree = repository().getCategoryTree().tree()

        assertThat(tree.pathOf(4552)?.map { it.treeId }).isEqualTo(listOf(4390, 4552))
    }

    @Test
    fun suggestionsReturnLeafIdOfEachSuggestedPath() = runTest {
        val result = repository().suggestCategories("Kinderfahrrad")

        assertThat(result).isEqualTo(Result.Success(listOf(4558, 6943)))
    }

    @Test
    fun blankTitleGivesNoSuggestionsWithoutRequest() = runTest {
        val result = repository().suggestCategories("   ")

        assertThat(result).isEqualTo(Result.Success(emptyList()))
        assertThat(requests.isEmpty()).isTrue()
    }

    @Test
    fun locationsAreMappedToIdAndLabel() = runTest {
        val result = repository().findLocations("1010")

        assertThat(result).isEqualTo(Result.Success(listOf(WillhabenLocation(117223, "Wien, 01. Bezirk, Innere Stadt"))))
    }

    private companion object {
        // Shape of the real response, reduced to two branches
        const val TREE_JSON = """
            {
              "categoryNode": {"treeId": 2, "code": "Marktplatz", "label": "Marktplatz", "attributeReferences": [], "systemTags": [],
                "children": [
                  {"treeId": 6941, "code": "ANTIQUES_ART", "label": "Antiquitäten / Kunst", "attributeReferences": [],
                    "children": [
                      {"treeId": 6943, "code": "COMICS_MANGAS", "label": "Comics / Mangas", "children": [],
                       "attributeReferences": [
                         {"required": true, "treeAttributeId": 21, "code": "Zustand", "selectionType": "SINGLE_SELECT"},
                         {"required": true, "treeAttributeId": 2535, "code": "Uebergabe", "selectionType": "MULTI_SELECT"}
                       ]}
                    ]},
                  {"treeId": 4390, "code": "SPORT_SPORTEQUIPMENT", "label": "Sport / Sportgeräte", "attributeReferences": [],
                    "children": [
                      {"treeId": 4552, "code": "BICYCLE", "label": "Fahrräder", "children": [], "attributeReferences": []}
                    ]}
                ]},
              "attributes": [
                {"treeAttributeElement": {"label": "Zustand", "treeId": 21, "code": "Zustand", "systemTags": []},
                 "values": [
                   {"label": "Neu", "treeId": 22, "code": "neu", "sortOrder": 10},
                   {"label": "Gebraucht", "treeId": 23, "code": "gebraucht", "sortOrder": 30}
                 ]}
              ],
              "dependentAttributes": []
            }
        """

        const val SUGGESTION_JSON = """
            {"categories": [
              {"id": 4390, "name": "Sport / Sportgeräte", "code": "SPORT_SPORTEQUIPMENT", "categories": [
                {"id": 4525, "name": "Fahrräder / Radsport", "code": "BIKES_CYCLING", "categories": [
                  {"id": 4558, "name": "Kinderfahrräder", "code": "CHILDREN_BICYCLES", "categories": []}
                ]}
              ]},
              {"id": 6941, "name": "Antiquitäten / Kunst", "code": "ANTIQUES_ART", "categories": [
                {"id": 6943, "name": "Comics / Mangas", "code": "COMICS_MANGAS", "categories": []}
              ]},
              {"id": 4390, "name": "Sport / Sportgeräte", "code": "SPORT_SPORTEQUIPMENT", "categories": [
                {"id": 4558, "name": "Kinderfahrräder", "code": "CHILDREN_BICYCLES", "categories": []}
              ]}
            ]}
        """
    }
}
