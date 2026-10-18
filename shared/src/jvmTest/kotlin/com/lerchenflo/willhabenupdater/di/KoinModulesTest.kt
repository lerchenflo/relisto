package com.lerchenflo.willhabenupdater.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import assertk.assertThat
import assertk.assertions.isNotNull
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.listings.data.DesktopImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.listings.presentation.editor.ListingEditorViewModel
import com.lerchenflo.willhabenupdater.listings.presentation.list.ListingsViewModel
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.WillhabenTabViewModel
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.willhabenupdater.settings.presentation.SettingsViewModel
import com.lerchenflo.willhabenupdater.testutil.TempAppDirectories
import com.lerchenflo.willhabenupdater.testutil.testPlatformModule
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private val koin = koinApplication {
        modules(testPlatformModule(dirs), datasourceModule, presentationModule)
    }.koin

    @BeforeTest
    // Work started by the ViewModels is queued and never run; this test only checks the wiring
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    private val created = mutableListOf<ViewModel>()

    private fun <T : ViewModel> T.tracked(): T = also { created += it }

    @AfterTest
    fun tearDown() {
        // Stop the ViewModels' coroutines before the test dispatcher goes away
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        koin.close()
        dirs.delete()
    }

    @Test
    fun listViewModelAndSettingsResolve() {
        assertThat(koin.get<ListingsViewModel>().tracked()).isNotNull()
        assertThat(koin.get<SettingsViewModel>().tracked()).isNotNull()
        assertThat(koin.get<AccountViewModel>().tracked()).isNotNull()
    }

    @Test
    fun listingScopedViewModelsResolveWithListingIdParameter() {
        assertThat(koin.get<ListingEditorViewModel> { parametersOf(1L) }.tracked()).isNotNull()
        assertThat(koin.get<WillhabenTabViewModel> { parametersOf(1L) }.tracked()).isNotNull()
    }
}
