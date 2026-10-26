package com.lerchenflo.relisto.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lerchenflo.relisto.app.domain.AppVersionProvider
import com.lerchenflo.relisto.datasource.database.AppDatabase
import com.lerchenflo.relisto.datasource.network.ResponseCaptureSettings
import com.lerchenflo.relisto.datasource.preferences.KSafeSecureStorage
import com.lerchenflo.relisto.datasource.preferences.SecureStorage
import com.lerchenflo.relisto.listings.data.DesktopImagePicker
import com.lerchenflo.relisto.listings.data.DesktopImageStorage
import com.lerchenflo.relisto.listings.domain.ImagePicker
import com.lerchenflo.relisto.listings.domain.ImageStorage
import com.lerchenflo.relisto.marketplace.willhaben.data.JavaFxWillhabenLoginBrowser
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenLoginBrowser
import com.lerchenflo.relisto.utilities.AppDirectories
import com.lerchenflo.relisto.utilities.DesktopAppDirectories
import com.lerchenflo.relisto.utilities.DesktopAppVersionProvider
import io.ktor.client.engine.HttpClientEngine
import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import java.io.File
import com.lerchenflo.relisto.backup.data.DesktopBackupFilePicker
import com.lerchenflo.relisto.backup.data.FileBackupArchive
import com.lerchenflo.relisto.backup.data.PendingRestore
import com.lerchenflo.relisto.backup.domain.BackupArchive
import com.lerchenflo.relisto.backup.domain.BackupFilePicker

/** Name of the encrypted store; KSafe scopes its key to this name. */
private const val SECURE_STORE_NAME = "relisto"

val desktopPlatformModule = module {
    single<AppDirectories> { DesktopAppDirectories() }
    single<RoomDatabase.Builder<AppDatabase>> {
        val directories = get<AppDirectories>()
        // A restored backup replaces the database before it is opened
        PendingRestore.of(directories).applyIfPresent()
        Room.databaseBuilder<AppDatabase>(directories.databaseFile)
    }
    single<DataStore<Preferences>> {
        createDataStore { File(get<AppDirectories>().dataDir, DATA_STORE_FILE_NAME).absolutePath }
    }
    single<HttpClientEngine> { OkHttp.create() }
    singleOf(::DesktopImageStorage) { bind<ImageStorage>() }
    singleOf(::DesktopImagePicker) { bind<ImagePicker>() }
    singleOf(::FileBackupArchive) { bind<BackupArchive>() }
    singleOf(::DesktopBackupFilePicker) { bind<BackupFilePicker>() }
    single<SecureStorage> {
        KSafeSecureStorage(KSafe(fileName = SECURE_STORE_NAME, baseDir = File(get<AppDirectories>().dataDir)))
    }
    singleOf(::JavaFxWillhabenLoginBrowser) { bind<WillhabenLoginBrowser>() }
    single { ResponseCaptureSettings(System.getProperty("willhaben.captureDir")) }
    single<AppVersionProvider> { DesktopAppVersionProvider() }
}
