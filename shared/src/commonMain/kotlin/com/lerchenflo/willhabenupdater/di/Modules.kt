package com.lerchenflo.willhabenupdater.di

import com.lerchenflo.willhabenupdater.app.navigation.Navigator
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.datasource.database.CreateAppDatabase
import com.lerchenflo.willhabenupdater.datasource.network.HttpClientFactory
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** All data-layer singletons. Platform modules provide the database builder, DataStore, engine and file access. */
val datasourceModule = module {
    single<AppDatabase> { CreateAppDatabase(get()).getDatabase() }
    single { get<AppDatabase>().listingDao() }
    single { get<AppDatabase>().listingImageDao() }
    single { get<AppDatabase>().publicationDao() }

    single<HttpClient> { HttpClientFactory.createWillhabenClient(get()) }

    singleOf(::Navigator)
}

/** All ViewModels. */
val presentationModule = module {
}
