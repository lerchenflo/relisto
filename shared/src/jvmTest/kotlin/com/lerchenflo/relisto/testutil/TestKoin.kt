package com.lerchenflo.relisto.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lerchenflo.relisto.app.domain.AppVersionProvider
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.datasource.network.ResponseCaptureSettings
import com.lerchenflo.relisto.datasource.preferences.SecureStorage
import com.lerchenflo.relisto.di.createDataStore
import com.lerchenflo.relisto.listings.data.DesktopImageStorage
import com.lerchenflo.relisto.listings.domain.ImagePicker
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.relisto.utilities.AppDirectories
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/** Desktop platform bindings with test doubles: temp folders, in-memory DB, offline network, no dialogs, no browser. */
fun testPlatformModule(dirs: TempAppDirectories): Module = module {
    single<AppDirectories> { dirs }
    single<RoomDatabase.Builder<AppDatabase>> { Room.inMemoryDatabaseBuilder<AppDatabase>() }
    single<DataStore<Preferences>> { createDataStore { File(dirs.root, "prefs.preferences_pb").absolutePath } }
    single<HttpClientEngine> { MockEngine { respondError(HttpStatusCode.ServiceUnavailable) } }
    single<ImageStorage> { DesktopImageStorage(get()) }
    single<ImagePicker> { object : ImagePicker { override suspend fun pickImages() = emptyList<String>() } }
    single<SecureStorage> { FakeSecureStorage() }
    single<WillhabenLoginBrowser> { FakeLoginBrowser() }
    single { ResponseCaptureSettings(null) }
    single<AppVersionProvider> { object : AppVersionProvider { override val currentVersion: String? = null } }
}
