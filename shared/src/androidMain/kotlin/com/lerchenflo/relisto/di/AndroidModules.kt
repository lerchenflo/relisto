package com.lerchenflo.relisto.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lerchenflo.relisto.app.domain.AppVersionProvider
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.datasource.network.ResponseCaptureSettings
import com.lerchenflo.relisto.datasource.preferences.KSafeSecureStorage
import com.lerchenflo.relisto.datasource.preferences.SecureStorage
import com.lerchenflo.relisto.listings.data.AndroidImagePicker
import com.lerchenflo.relisto.listings.data.AndroidImageStorage
import com.lerchenflo.relisto.listings.domain.ImagePicker
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.marketplace.willhaben.data.AndroidWillhabenLoginBrowser
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.relisto.utilities.AndroidAppDirectories
import com.lerchenflo.relisto.utilities.AndroidAppVersionProvider
import com.lerchenflo.relisto.utilities.AppDirectories
import io.ktor.client.engine.HttpClientEngine
import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import java.io.File

val androidPlatformModule = module {
    single<AppDirectories> { AndroidAppDirectories(androidContext()) }
    single<RoomDatabase.Builder<AppDatabase>> {
        val context = androidContext()
        Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(AppDatabase.DB_NAME).absolutePath)
    }
    single<DataStore<Preferences>> {
        createDataStore { File(get<AppDirectories>().dataDir, DATA_STORE_FILE_NAME).absolutePath }
    }
    single<HttpClientEngine> { OkHttp.create() }
    singleOf(::AndroidImageStorage) { bind<ImageStorage>() }
    singleOf(::AndroidImagePicker) { bind<ImagePicker>() }
    single<SecureStorage> { KSafeSecureStorage(KSafe(androidContext(), fileName = "relisto")) }
    singleOf(::AndroidWillhabenLoginBrowser) { bind<WillhabenLoginBrowser>() }
    single { ResponseCaptureSettings(null) }
    single<AppVersionProvider> { AndroidAppVersionProvider(androidContext()) }
}

/** Starts Koin once; called from the Android entry point. */
fun initKoin(context: Context) {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            androidContext(context.applicationContext)
            modules(androidPlatformModule, datasourceModule, presentationModule)
        }
    }
}
