package com.lerchenflo.willhabenupdater.listings.presentation.editor

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.testutil.FakeImagePicker
import com.lerchenflo.willhabenupdater.testutil.FakeListingRepository
import com.lerchenflo.willhabenupdater.testutil.MainDispatcherTest
import com.lerchenflo.willhabenupdater.testutil.image
import com.lerchenflo.willhabenupdater.testutil.listing
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ListingEditorViewModelTest : MainDispatcherTest() {

    private val repository = FakeListingRepository()
    private val picker = FakeImagePicker()

    init {
        repository.listings.value = listOf(
            listing(
                1,
                title = "Kinderfahrrad",
                description = "Gut erhalten",
                priceCents = 4550,
                postcode = "6850",
                images = listOf(image(10, 1, 0), image(11, 1, 1), image(12, 1, 2)),
            ).copy(condition = Condition.USED)
        )
    }

    private fun viewModel(listingId: Long = 1) = ListingEditorViewModel(listingId, repository, picker)

    @Test
    fun listingIsLoadedIntoFields() = runTest {
        val state = viewModel().state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.title).isEqualTo("Kinderfahrrad")
        assertThat(state.description).isEqualTo("Gut erhalten")
        assertThat(state.priceText).isEqualTo("45,50")
        assertThat(state.condition).isEqualTo(Condition.USED)
        assertThat(state.postcode).isEqualTo("6850")
        assertThat(state.images.map { it.id }).containsExactly(10L, 11L, 12L)
    }

    @Test
    fun unknownListingIsReportedAsNotFound() = runTest {
        assertThat(viewModel(listingId = 99).state.value.notFound).isTrue()
    }

    @Test
    fun editedTextIsSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnTitleChange("Kinderfahrrad 20 Zoll"))
        viewModel.onAction(ListingEditorAction.OnDescriptionChange("Wie neu"))

        assertThat(repository.updatedContents.last()).isEqualTo(
            1L to ListingContent("Kinderfahrrad 20 Zoll", "Wie neu", 4550, Condition.USED, "6850")
        )
    }

    @Test
    fun validPriceIsSavedAsCents() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnPriceChange("1.200"))

        assertThat(repository.updatedContents.last().second.priceCents).isEqualTo(120000L)
        assertThat(viewModel.state.value.isPriceInvalid).isFalse()
    }

    @Test
    fun invalidPriceIsFlaggedAndKeepsLastValidPrice() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnPriceChange("12,345"))

        assertThat(viewModel.state.value.isPriceInvalid).isTrue()
        assertThat(viewModel.state.value.priceText).isEqualTo("12,345")
        assertThat(repository.listings.value.single().priceCents).isEqualTo(4550L)
    }

    @Test
    fun emptyPriceRemovesPrice() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnPriceChange(""))

        assertThat(repository.updatedContents.last().second.priceCents).isEqualTo(null)
        assertThat(viewModel.state.value.isPriceInvalid).isFalse()
    }

    @Test
    fun conditionAndPostcodeAreSaved() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnConditionChange(Condition.NEW))
        viewModel.onAction(ListingEditorAction.OnPostcodeChange("1010"))

        val saved = repository.updatedContents.last().second
        assertThat(saved.condition).isEqualTo(Condition.NEW)
        assertThat(saved.postcode).isEqualTo("1010")
    }

    @Test
    fun postcodeKeepsOnlyFourDigits() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnPostcodeChange("10a105"))

        assertThat(viewModel.state.value.postcode).isEqualTo("1010")
    }

    @Test
    fun databaseUpdatesRefreshImagesButKeepTypedText() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ListingEditorAction.OnTitleChange("Getippt"))

        repository.listings.value = listOf(
            repository.listings.value.single().copy(title = "Alt", images = listOf(image(13, 1, 0)))
        )

        assertThat(viewModel.state.value.title).isEqualTo("Getippt")
        assertThat(viewModel.state.value.images.map { it.id }).containsExactly(13L)
    }

    @Test
    fun pickedImagesAreAdded() = runTest {
        picker.result = listOf("/a.jpg", "/b.jpg")
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnAddImagesClick)

        assertThat(repository.addedImagePaths).containsExactly(listOf("/a.jpg", "/b.jpg"))
    }

    @Test
    fun cancelledPickerAddsNothing() = runTest {
        picker.result = emptyList()
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnAddImagesClick)

        assertThat(repository.addedImagePaths).isEmpty()
    }

    @Test
    fun droppedFilesAreAdded() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnFilesDropped(listOf("/c.png")))

        assertThat(repository.addedImagePaths).containsExactly(listOf("/c.png"))
    }

    @Test
    fun failedImagesShowMessage() = runTest {
        repository.failingImagePaths = setOf("/broken.heic")
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ListingEditorAction.OnFilesDropped(listOf("/ok.jpg", "/broken.heic")))
            assertThat(awaitItem()).isInstanceOf(ListingEditorEvent.ShowMessage::class)
        }
    }

    @Test
    fun removingImageDeletesIt() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnRemoveImageClick(11))

        assertThat(repository.removedImageIds).containsExactly(11L)
    }

    @Test
    fun movingImageReordersImmediatelyAndSavesOrder() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnMoveImage(fromIndex = 2, toIndex = 0))

        assertThat(viewModel.state.value.images.map { it.id }).containsExactly(12L, 10L, 11L)
        assertThat(repository.reorders.last()).containsExactly(12L, 10L, 11L)
    }

    @Test
    fun archiveToggleArchivesActiveListing() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnArchiveToggleClick)

        assertThat(repository.listings.value.single().archived).isTrue()
        assertThat(viewModel.state.value.archived).isTrue()
    }

    @Test
    fun duplicateSendsNewListingId() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(ListingEditorAction.OnDuplicateClick)
            val event = awaitItem() as ListingEditorEvent.ListingDuplicated
            assertThat(repository.listings.value.map { it.id }).containsExactly(1L, event.newListingId)
        }
    }

    @Test
    fun deleteNeedsConfirmation() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(ListingEditorAction.OnDeleteClick)

        assertThat(viewModel.state.value.showDeleteDialog).isTrue()
        assertThat(repository.deletedIds).isEmpty()
    }

    @Test
    fun confirmedDeleteRemovesListing() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ListingEditorAction.OnDeleteClick)

        viewModel.onAction(ListingEditorAction.OnDeleteConfirm)

        assertThat(repository.deletedIds).containsExactly(1L)
        assertThat(viewModel.state.value.showDeleteDialog).isFalse()
    }

    @Test
    fun dismissedDeleteKeepsListing() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(ListingEditorAction.OnDeleteClick)

        viewModel.onAction(ListingEditorAction.OnDeleteDismiss)

        assertThat(viewModel.state.value.showDeleteDialog).isFalse()
        assertThat(repository.deletedIds).isEmpty()
    }
}
