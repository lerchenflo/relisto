package com.lerchenflo.relisto.settings.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.lerchenflo.relisto.app.navigation.NavigationAction
import com.lerchenflo.relisto.app.navigation.Navigator
import com.lerchenflo.relisto.listings.domain.Condition
import com.lerchenflo.relisto.settings.domain.AppSettings
import com.lerchenflo.relisto.testutil.FakeSettingsDataSource
import com.lerchenflo.relisto.testutil.MainDispatcherTest
import com.lerchenflo.relisto.utilities.AppDirectories
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.backup.domain.BackupFilePicker
import com.lerchenflo.relisto.backup.domain.BackupRepository
import com.lerchenflo.relisto.testutil.FakeTimeProvider
import com.lerchenflo.relisto.utilities.UiText
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.backup_done
import relisto.shared.generated.resources.backup_restore_failed
import relisto.shared.generated.resources.backup_restore_ready

class SettingsViewModelTest : MainDispatcherTest() {

    private val settings = FakeSettingsDataSource(
        AppSettings(
            defaultPostcode = "6850",
            defaultCondition = Condition.USED,
            defaultPickup = true,
            defaultShipping = true,
            defaultShippingPriceCents = 590,
        )
    )
    private val navigator = Navigator()
    private val directories = object : AppDirectories {
        override val dataDir = "/data/Relisto"
        override val databaseFile = "/data/Relisto/relisto.db"
        override val imagesDir = "/data/Relisto/images"
        override val cacheDir = "/data/Relisto/cache"
    }

    private val backup = FakeBackupRepository()
    private val picker = FakeBackupFilePicker()

    private fun viewModel() = SettingsViewModel(settings, navigator, directories, backup, picker, FakeTimeProvider(now = 1_761_480_000_000L))

    @Test
    fun storedSettingsAreShown() = runTest {
        val state = viewModel().state.value

        assertThat(state.postcode).isEqualTo("6850")
        assertThat(state.condition).isEqualTo(Condition.USED)
        assertThat(state.pickup).isTrue()
        assertThat(state.shipping).isTrue()
        assertThat(state.shippingPriceText).isEqualTo("5,90")
        assertThat(state.dataFolder).isEqualTo("/data/Relisto")
    }

    @Test
    fun postcodeKeepsFourDigitsAndIsSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnPostcodeChange("1a0105"))

        assertThat(viewModel.state.value.postcode).isEqualTo("1010")
        assertThat(settings.settings.value.defaultPostcode).isEqualTo("1010")
    }

    @Test
    fun conditionIsSaved() = runTest {
        viewModel().onAction(SettingsAction.OnConditionChange(null))

        assertThat(settings.settings.value.defaultCondition).isNull()
    }

    @Test
    fun deliveryTogglesAreSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnPickupChange(false))
        viewModel.onAction(SettingsAction.OnShippingChange(false))

        assertThat(settings.settings.value.defaultPickup).isFalse()
        assertThat(settings.settings.value.defaultShipping).isFalse()
    }

    @Test
    fun validShippingPriceIsSaved() = runTest {
        viewModel().onAction(SettingsAction.OnShippingPriceChange("4,50"))

        assertThat(settings.settings.value.defaultShippingPriceCents).isEqualTo(450L)
    }

    @Test
    fun emptyShippingPriceRemovesIt() = runTest {
        viewModel().onAction(SettingsAction.OnShippingPriceChange(""))

        assertThat(settings.settings.value.defaultShippingPriceCents).isNull()
    }

    @Test
    fun invalidShippingPriceIsFlaggedAndNotSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnShippingPriceChange("gratis"))

        assertThat(viewModel.state.value.isShippingPriceInvalid).isTrue()
        assertThat(settings.settings.value.defaultShippingPriceCents).isEqualTo(590L)
    }

    @Test
    fun backClickNavigatesBack() = runTest {
        val viewModel = viewModel()

        navigator.navigationActions.test {
            viewModel.onAction(SettingsAction.OnBackClick)
            assertThat(awaitItem()).isEqualTo(NavigationAction.NavigateBack())
        }
    }

    @Test
    fun autoRenewIsShownAndSaved() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.state.value.autoRenewExpired).isFalse()

        viewModel.onAction(SettingsAction.OnAutoRenewChange(true))

        assertThat(viewModel.state.value.autoRenewExpired).isTrue()
        assertThat(settings.settings.value.autoRenewExpired).isTrue()
    }

    @Test
    fun backupIsWrittenToTheChosenFile() = runTest {
        picker.saveTarget = "/x/backup.zip"
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnExportBackupClick)

            assertThat(picker.suggestedName).isEqualTo("Relisto-Backup-2025-10-26.zip")
            assertThat(backup.exportedTo).isEqualTo("/x/backup.zip")
            assertThat(awaitItem()).isEqualTo(SettingsEvent.ShowMessage(UiText.StringResourceText(Res.string.backup_done)))
        }
    }

    @Test
    fun cancelledDialogsDoNothing() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnExportBackupClick)
        viewModel.onAction(SettingsAction.OnRestoreBackupClick)

        assertThat(backup.exportedTo).isNull()
        assertThat(viewModel.state.value.restoreSource).isNull()
    }

    @Test
    fun restoreIsConfirmedBeforeTheBackupIsUnpacked() = runTest {
        picker.backupFile = "/x/backup.zip"
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnRestoreBackupClick)
        assertThat(viewModel.state.value.restoreSource).isEqualTo("/x/backup.zip")
        assertThat(backup.restoredFrom).isNull()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreConfirm)

            assertThat(backup.restoredFrom).isEqualTo("/x/backup.zip")
            assertThat(viewModel.state.value.restoreSource).isNull()
            assertThat(awaitItem()).isEqualTo(SettingsEvent.ShowMessage(UiText.StringResourceText(Res.string.backup_restore_ready)))
        }
    }

    @Test
    fun fileWithoutBackupIsReported() = runTest {
        picker.backupFile = "/x/other.zip"
        backup.restoreResult = Result.Error(DataError.Local.NOT_FOUND)
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnRestoreBackupClick)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreConfirm)

            assertThat(awaitItem()).isEqualTo(SettingsEvent.ShowMessage(UiText.StringResourceText(Res.string.backup_restore_failed)))
        }
    }
}

private class FakeBackupRepository : BackupRepository {
    var exportedTo: String? = null
    var restoredFrom: String? = null
    var restoreResult: EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun export(destination: String): EmptyResult<DataError.Local> {
        exportedTo = destination
        return Result.Success(Unit)
    }

    override suspend fun prepareRestore(source: String): EmptyResult<DataError.Local> {
        restoredFrom = source
        return restoreResult
    }
}

private class FakeBackupFilePicker : BackupFilePicker {
    var saveTarget: String? = null
    var backupFile: String? = null
    var suggestedName: String? = null

    override suspend fun pickSaveTarget(suggestedName: String): String? {
        this.suggestedName = suggestedName
        return saveTarget
    }

    override suspend fun pickBackupFile(): String? = backupFile
}
