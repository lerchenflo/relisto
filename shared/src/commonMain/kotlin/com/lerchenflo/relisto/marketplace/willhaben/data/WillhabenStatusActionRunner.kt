package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.marketplace.domain.Marketplace
import com.lerchenflo.relisto.marketplace.willhaben.domain.BulkActionResult
import com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenImporter
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusAction
import com.lerchenflo.relisto.marketplace.willhaben.domain.WillhabenStatusActions

/**
 * Status actions like "Meine Anzeigen" in the web client: a request without body on the link
 * willhaben offered at the last import - `DELETE` for deleting, `PUT` for everything else.
 */
class WillhabenStatusActionRunner(
    private val publicationDao: PublicationDao,
    private val writeDataSource: KtorWillhabenWriteDataSource,
    private val requestRunner: WillhabenRequestRunner,
    private val importer: WillhabenImporter,
) : WillhabenStatusActions {

    override suspend fun perform(listingId: Long, action: WillhabenStatusAction): EmptyResult<StatusActionError> {
        val result = send(listingId, action)
        // Status and the offered actions change with every action
        if (result is Result.Success) importer.importMyListings()
        return result
    }

    override suspend fun performAll(listingIds: List<Long>, action: WillhabenStatusAction): BulkActionResult {
        val succeeded = mutableSetOf<Long>()
        var firstError: StatusActionError? = null
        listingIds.forEach { listingId ->
            when (val result = send(listingId, action)) {
                is Result.Success -> succeeded += listingId
                is Result.Error -> if (firstError == null) firstError = result.error
            }
        }
        if (succeeded.isNotEmpty()) importer.importMyListings()
        return BulkActionResult(succeeded = succeeded, firstError = firstError)
    }

    private suspend fun send(listingId: Long, action: WillhabenStatusAction): EmptyResult<StatusActionError> {
        val publication = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)
        val link = publication?.remoteActionsJson?.toStatusActionLinks()?.firstOrNull { it.action == action }
            ?: return Result.Error(StatusActionError.NotAvailable)
        val route = ContextLink(link.serviceName, link.relativePath).route
        val result = requestRunner.run {
            if (action == WillhabenStatusAction.DELETE) writeDataSource.delete(route) else writeDataSource.put(route)
        }
        return when (result) {
            is Result.Error -> Result.Error(StatusActionError.Failed(result.error))
            is Result.Success -> Result.Success(Unit)
        }
    }
}
