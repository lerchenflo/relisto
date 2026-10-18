package com.lerchenflo.willhabenupdater.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import assertk.assertThat
import assertk.assertions.isNotNull
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.listings.data.DesktopImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.listings.presentation.editor.ListingEditorViewModel
import com.lerchenflo.willhabenupdater.listings.presentation.list.ListingsViewModel
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.WillhabenTabViewModel
import com.lerchenflo.willhabenupdater.settings.presentation.SettingsViewModel
import com.lerchenflo.willhabenupdater.testutil.TempAppDirectories
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KoinModulesTest {

    private val dirs = TempAppDirectories()

    /** Same bindings as the desktop platform module, with test doubles for disk, network and dialogs. */
    private val testPlatformModule = module {
        single<AppDirectories> { dirs }
        single<RoomDatabase.Builder<AppDatabase>> { Room.inMemoryDatabaseBuilder<AppDatabase>() }
        single<DataStore<Preferences>> { createDataStore { File(dirs.root, "prefs.preferences_pb").absolutePath } }
        single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.ServiceUnavailable) } }
        single<ImageStorage> { DesktopImageStorage(get()) }
        single<ImagePicker> { object : ImagePicker { override suspend fun pickImages() = emptyList<String>() } }
    }

    private val koin = koinApplication {
        modules(testPlatformModule, datasourceModule, presentationModule)
    }.koin

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        koin.close()
        dirs.delete()
    }

    @Test
    fun listViewModelAndSettingsResolve() {
        assertThat(koin.get<ListingsViewModel>()).isNotNull()
        assertThat(koin.get<SettingsViewModel>()).isNotNull()
    }

    @Test
    fun listingScopedViewModelsResolveWithListingIdParameter() {
        assertThat(koin.get<ListingEditorViewModel> { parametersOf(1L) }).isNotNull()
        assertThat(koin.get<WillhabenTabViewModel> { parametersOf(1L) }).isNotNull()
    }
}
