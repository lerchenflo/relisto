package com.lerchenflo.relisto.datasource.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test

/** Every schema change keeps the user's data: the old database is migrated, never recreated. */
class AppDatabaseMigrationTest {

    private val directory = Files.createTempDirectory("relisto-migration").toFile()
    private val databaseFile = File(directory, AppDatabase.DB_NAME)

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = Path("schemas"),
        databasePath = Path(databaseFile.absolutePath),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun version1ListingsAndPublicationsSurviveMigrationTo2() {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO listings (id, title, description, priceCents, condition, postcode, archived, createdAt, updatedAt) VALUES (1, 'Rasentraktor', 'Text', 18000, 'USED', '6850', 0, 1, 2)")
            execSQL("INSERT INTO listing_publications (id, listingId, marketplace, remoteId, syncState) VALUES (1, 1, 'WILLHABEN', '111', 'SYNCED')")
            close()
        }

        val connection = helper.runMigrationsAndValidate(2)

        connection.prepare("SELECT title FROM listings WHERE id = 1").use { statement ->
            statement.step()
            assertThat(statement.getText(0)).isEqualTo("Rasentraktor")
        }
        connection.prepare("SELECT remoteId, replacedRemoteIds FROM listing_publications WHERE id = 1").use { statement ->
            statement.step()
            assertThat(statement.getText(0)).isEqualTo("111")
            assertThat(statement.getText(1).takeUnless { statement.isNull(1) }).isNull()
        }
        connection.close()
    }

    @Test
    fun version2PublicationsSurviveMigrationTo3WithoutStatusActions() {
        helper.createDatabase(2).apply {
            execSQL("INSERT INTO listings (id, title, description, priceCents, condition, postcode, archived, createdAt, updatedAt) VALUES (1, 'Rasentraktor', 'Text', 18000, 'USED', '6850', 0, 1, 2)")
            execSQL("INSERT INTO listing_publications (id, listingId, marketplace, remoteId, syncState, replacedRemoteIds) VALUES (1, 1, 'WILLHABEN', '111', 'SYNCED', '100')")
            close()
        }

        val connection = helper.runMigrationsAndValidate(3)

        connection.prepare("SELECT remoteId, replacedRemoteIds, remoteActionsJson FROM listing_publications WHERE id = 1").use { statement ->
            statement.step()
            assertThat(statement.getText(0)).isEqualTo("111")
            assertThat(statement.getText(1)).isEqualTo("100")
            assertThat(statement.isNull(2)).isEqualTo(true)
        }
        connection.close()
    }
}
