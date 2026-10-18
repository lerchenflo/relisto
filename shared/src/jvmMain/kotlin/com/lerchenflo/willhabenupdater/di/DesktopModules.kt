package com.lerchenflo.willhabenupdater.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lerchenflo.willhabenupdater.datasource.database.AppDatabase
import com.lerchenflo.willhabenupdater.listings.data.DesktopImagePicker
import com.lerchenflo.willhabenupdater.listings.data.DesktopImageStorage
import com.lerchenflo.willhabenupdater.listings.domain.ImagePicker
import com.lerchenflo.willhabenupdater.listings.domain.ImageStorage
import com.lerchenflo.willhabenupdater.utilities.AppDirectories
import com.lerchenflo.willhabenupdater.utilities.DesktopAppDirectories
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File

val desktopPlatformModule = module {
    single<AppDirectories> { DesktopAppDirectories() }
    single<RoomDatabase.Builder<AppDatabase>> {
        val dbFile = File(get<AppDirectories>().dataDir, AppDatabase.DB_NAME)
        Room.databaseBuilder<AppDatabase>(dbFile.absolutePath)
    }
    single<DataStore<Preferences>> {
        createDataStore { File(get<AppDirectories>().dataDir, DATA_STORE_FILE_NAME).absolutePath }
    }
    single<HttpClientEngine> { OkHttp.create() }
    singleOf(::DesktopImageStorage) { bind<ImageStorage>() }
    singleOf(::DesktopImagePicker) { bind<ImagePicker>() }
}
