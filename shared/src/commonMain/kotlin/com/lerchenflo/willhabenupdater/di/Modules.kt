package com.lerchenflo.willhabenupdater.di

import com.lerchenflo.willhabenupdater.app.data.GitHubUpdateChecker
import com.lerchenflo.willhabenupdater.app.domain.UpdateChecker
import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.app.presentation.AppUpdateViewModel
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.datasource.database.CreateAppDatabase
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import com.lerchenflo.willhabenupdater.datasource.network.PersistentCookiesStorage
import com.lerchenflo.willhabenupdater.datasource.network.ResponseCaptureSettings
import com.lerchenflo.willhabenupdater.datasource.preferences.Preferencemanager
import com.lerchenflo.willhabenupdater.listings.data.KtorImageDownloader
import com.lerchenflo.willhabenupdater.listings.data.RoomListingRepository
import com.lerchenflo.willhabenupdater.listings.domain.ImageDownloader
import com.lerchenflo.willhabenupdater.listings.presentation.editor.ListingEditorViewModel
import com.lerchenflo.willhabenupdater.listings.presentation.list.ListingsViewModel
import com.lerchenflo.willhabenupdater.listings.domain.ListingRepository
import com.lerchenflo.willhabenupdater.marketplace.data.RoomPublicationStatusRepository
import com.lerchenflo.willhabenupdater.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.CachedWillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.CookieWillhabenSessionRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.KtorWillhabenWriteDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.RoomWillhabenImporter
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.WillhabenRepublisher
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.WillhabenListingsSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.KtorWillhabenListingsDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.WillhabenListingsFetcher
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.WillhabenRequestRunner
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.KtorWillhabenAccountDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.KtorWillhabenCatalogDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.data.RoomWillhabenFieldsDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenPublisher
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenSessionRepository
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.WillhabenTabViewModel
import com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.willhabenupdater.settings.domain.SettingsDataSource
import com.lerchenflo.willhabenupdater.settings.presentation.SettingsViewModel
import com.lerchenflo.willhabenupdater.utilities.SystemTimeProvider
import com.lerchenflo.willhabenupdater.utilities.TimeProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * All data-layer singletons. Platform modules provide the database builder, DataStore, HTTP engine,
 * file access, secure storage and the login browser.
 */
val datasourceModule = module {
    single<AppDatabase> { CreateAppDatabase(get()).getDatabase() }
    single { get<AppDatabase>().listingDao() }
    single { get<AppDatabase>().listingImageDao() }
    single { get<AppDatabase>().publicationDao() }

    singleOf(::SystemTimeProvider) { bind<TimeProvider>() }
    singleOf(::Preferencemanager) { bind<SettingsDataSource>() }
    singleOf(::PersistentCookiesStorage)
    single<HttpClient> {
        HttpClientFactory.createWillhabenClient(get(), get<PersistentCookiesStorage>(), get<ResponseCaptureSettings>().directory)
    }

    singleOf(::RoomListingRepository) { bind<ListingRepository>() }
    singleOf(::RoomPublicationStatusRepository) { bind<PublicationStatusRepository>() }
    singleOf(::RoomWillhabenFieldsDataSource) { bind<WillhabenFieldsLocalDataSource>() }
    singleOf(::KtorWillhabenCatalogDataSource)
    singleOf(::CachedWillhabenCatalogRepository) { bind<WillhabenCatalogRepository>() }
    singleOf(::KtorWillhabenAccountDataSource)
    singleOf(::CookieWillhabenSessionRepository) { bind<WillhabenSessionRepository>() }
    single { WillhabenRequestRunner(renewSession = { get<KtorWillhabenAccountDataSource>().renewSession() }, backoff = { delay(it) }) }
    singleOf(::KtorWillhabenListingsDataSource)
    single<WillhabenListingsSource> { WillhabenListingsFetcher(get(), get()) }
    single<HttpClient>(named(IMAGE_CLIENT)) { HttpClientFactory.createImageClient(get()) }
    single<ImageDownloader> { KtorImageDownloader(get(named(IMAGE_CLIENT))) }
    singleOf(::RoomWillhabenImporter) { bind<WillhabenImporter>() }
    singleOf(::KtorWillhabenWriteDataSource)
    singleOf(::WillhabenRepublisher) { bind<WillhabenPublisher>() }

    // Separate plain client for the optional update hint (no willhaben headers or cookies)
    single<HttpClient>(named(GITHUB_CLIENT)) { HttpClientFactory.createGitHubClient(get()) }
    single<UpdateChecker> { GitHubUpdateChecker(get(named(GITHUB_CLIENT)), get()) }

    singleOf(::Navigator)
}

private const val GITHUB_CLIENT = "github"
private const val IMAGE_CLIENT = "images"


/** All ViewModels. */
val presentationModule = module {
    viewModelOf(::ListingsViewModel)
    // The listing id comes from parametersOf(listingId) at the injection site
    viewModelOf(::ListingEditorViewModel)
    viewModelOf(::WillhabenTabViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::AppUpdateViewModel)
}
