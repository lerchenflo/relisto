package com.lerchenflo.relisto.di

import com.lerchenflo.relisto.datasource.network.RequestSpacing
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenListingCreator
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCreator
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenDraftFinisher
import com.lerchenflo.relisto.activity.presentation.ActivityLogViewModel
import org.koin.core.module.dsl.viewModel
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenImageUploader
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenListingUpdater
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenUpdater
import com.lerchenflo.relisto.activity.data.RoomActivityLog
import com.lerchenflo.relisto.activity.domain.ActivityLog
import com.lerchenflo.relisto.app.data.GitHubUpdateChecker
import com.lerchenflo.relisto.app.domain.UpdateChecker
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.app.presentation.AppUpdateViewModel
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.datasource.database.CreateAppDatabase
import com.lerchenflo.relisto.datasource.network.HttpClientFactory
import com.lerchenflo.relisto.datasource.network.PersistentCookiesStorage
import com.lerchenflo.relisto.datasource.network.ResponseCaptureSettings
import com.lerchenflo.relisto.datasource.preferences.Preferencemanager
import com.lerchenflo.relisto.listings.data.KtorImageDownloader
import com.lerchenflo.relisto.listings.data.RoomListingRepository
import com.lerchenflo.relisto.listings.domain.ImageDownloader
import com.lerchenflo.relisto.listings.presentation.editor.ListingEditorViewModel
import com.lerchenflo.relisto.listings.presentation.list.ListingsViewModel
import com.lerchenflo.relisto.listings.domain.ListingRepository
import com.lerchenflo.relisto.marketplace.data.RoomPublicationStatusRepository
import com.lerchenflo.relisto.marketplace.domain.PublicationStatusRepository
import com.lerchenflo.relisto.marketplace.willhaben.data.CachedWillhabenCatalogRepository
import com.lerchenflo.relisto.marketplace.willhaben.data.CookieWillhabenSessionRepository
import com.lerchenflo.relisto.marketplace.willhaben.data.KtorWillhabenWriteDataSource
import com.lerchenflo.relisto.marketplace.willhaben.data.RoomWillhabenImporter
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenRepublisher
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenStatusActionRunner
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActions
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenListingsSource
import com.lerchenflo.relisto.marketplace.willhaben.data.KtorWillhabenListingsDataSource
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenListingsFetcher
import com.lerchenflo.relisto.marketplace.willhaben.data.WillhabenRequestRunner
import com.lerchenflo.relisto.marketplace.willhaben.data.KtorWillhabenAccountDataSource
import com.lerchenflo.relisto.marketplace.willhaben.data.KtorWillhabenCatalogDataSource
import com.lerchenflo.relisto.marketplace.willhaben.data.RoomWillhabenFieldsDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenCatalogRepository
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenFieldsLocalDataSource
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenPublisher
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenRenewer
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenSessionRepository
import com.lerchenflo.relisto.marketplace.willhaben.presentation.WillhabenTabViewModel
import com.lerchenflo.relisto.marketplace.willhaben.presentation.account.AccountViewModel
import com.lerchenflo.relisto.settings.domain.SettingsDataSource
import com.lerchenflo.relisto.settings.presentation.SettingsViewModel
import com.lerchenflo.relisto.utilities.SystemTimeProvider
import com.lerchenflo.relisto.utilities.TimeProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import com.lerchenflo.relisto.backup.data.DefaultBackupRepository
import com.lerchenflo.relisto.backup.domain.BackupRepository

/**
 * All data-layer singletons. Platform modules provide the database builder, DataStore, HTTP engine,
 * file access, secure storage and the login browser.
 */
val datasourceModule = module {
    singleOf(::DefaultBackupRepository) { bind<BackupRepository>() }
    single<AppDatabase> { CreateAppDatabase(get()).getDatabase() }
    single { get<AppDatabase>().listingDao() }
    single { get<AppDatabase>().listingImageDao() }
    single { get<AppDatabase>().publicationDao() }
    single { get<AppDatabase>().activityLogDao() }

    singleOf(::SystemTimeProvider) { bind<TimeProvider>() }
    singleOf(::Preferencemanager) { bind<SettingsDataSource>() }
    singleOf(::PersistentCookiesStorage)
    single<HttpClient> {
        HttpClientFactory.createWillhabenClient(get(), get<PersistentCookiesStorage>(), get<ResponseCaptureSettings>().directory)
    }

    singleOf(::RoomListingRepository) { bind<ListingRepository>() }
    singleOf(::RoomPublicationStatusRepository) { bind<PublicationStatusRepository>() }
    singleOf(::RoomActivityLog) { bind<ActivityLog>() }
    singleOf(::RoomWillhabenFieldsDataSource) { bind<WillhabenFieldsLocalDataSource>() }
    singleOf(::KtorWillhabenCatalogDataSource)
    singleOf(::CachedWillhabenCatalogRepository) { bind<WillhabenCatalogRepository>() }
    singleOf(::KtorWillhabenAccountDataSource)
    singleOf(::CookieWillhabenSessionRepository) { bind<WillhabenSessionRepository>() }
    single {
        WillhabenRequestRunner(
            renewSession = { get<KtorWillhabenAccountDataSource>().renewSession() },
            backoff = { delay(it) },
            spacing = RequestSpacing(WILLHABEN_REQUEST_SPACING_MILLIS),
        )
    }
    singleOf(::KtorWillhabenListingsDataSource)
    single<WillhabenListingsSource> { WillhabenListingsFetcher(get(), get()) }
    single<HttpClient>(named(IMAGE_CLIENT)) { HttpClientFactory.createImageClient(get()) }
    single<ImageDownloader> { KtorImageDownloader(get(named(IMAGE_CLIENT)), RequestSpacing(WILLHABEN_REQUEST_SPACING_MILLIS)) }
    singleOf(::RoomWillhabenImporter) { bind<WillhabenImporter>() }
    singleOf(::KtorWillhabenWriteDataSource)
    singleOf(::WillhabenImageUploader)
    singleOf(::WillhabenDraftFinisher)
    singleOf(::WillhabenRepublisher) { bind<WillhabenPublisher>() }
    singleOf(::WillhabenListingUpdater) { bind<WillhabenUpdater>() }
    singleOf(::WillhabenListingCreator) { bind<WillhabenCreator>() }
    singleOf(::WillhabenStatusActionRunner) { bind<WillhabenStatusActions>() }
    singleOf(::WillhabenRenewer)

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
    viewModel { params -> ActivityLogViewModel(params.getOrNull(), get(), get()) }
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::AppUpdateViewModel)
}

/** Pause between two willhaben requests (API and image server), so imports and bulk actions stay gentle. */
private const val WILLHABEN_REQUEST_SPACING_MILLIS = 300L
