package com.lerchenflo.willhabenupdater.di

import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.datasource.database.CreateAppDatabase
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.datasource.preferences.Preferencemanager
import com.lerchenflo.willhabenupdater.listings.data.RoomListingRepository
import com.lerchenflo.willhabenupdater.listings.presentation.editor.ListingEditorViewModel
import com.lerchenflo.willhabenupdater.listings.presentation.list.ListingsViewModel
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.CachedWillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.KtorWillhabenCatalogDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.RoomWillhabenFieldsDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.WillhabenTabViewModel
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import com.lerchenflo.willhabenupdater.settings.presentation.SettingsViewModel
import com.lerchenflo.willhabenupdater.utilities.SystemTimeProvider
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** All data-layer singletons. Platform modules provide the database builder, DataStore, engine and file access. */
val datasourceModule = module {
    single<AppDatabase> { CreateAppDatabase(get()).getDatabase() }
    single { get<AppDatabase>().listingDao() }
    single { get<AppDatabase>().listingImageDao() }
    single { get<AppDatabase>().publicationDao() }

    singleOf(::SystemTimeProvider) { bind<TimeProvider>() }
    singleOf(::Preferencemanager) { bind<SettingsDataSource>() }
    single<HttpClient> { HttpClientFactory.createWillhabenClient(get()) }

    singleOf(::RoomListingRepository) { bind<ListingRepository>() }
    singleOf(::RoomWillhabenFieldsDataSource) { bind<WillhabenFieldsLocalDataSource>() }
    singleOf(::KtorWillhabenCatalogDataSource)
    singleOf(::CachedWillhabenCatalogRepository) { bind<WillhabenCatalogRepository>() }

    singleOf(::Navigator)
}

/** All ViewModels. */
val presentationModule = module {
    viewModelOf(::ListingsViewModel)
    // The listing id comes from parametersOf(listingId) at the injection site
    viewModelOf(::ListingEditorViewModel)
    viewModelOf(::WillhabenTabViewModel)
    viewModelOf(::SettingsViewModel)
}
