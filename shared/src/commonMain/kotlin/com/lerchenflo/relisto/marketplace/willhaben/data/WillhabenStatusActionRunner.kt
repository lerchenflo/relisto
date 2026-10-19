package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.datasource.database.PublicationDao
import com.lerchenflo.relisto.marketplace.domain.Marketplace
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
        val publication = publicationDao.getByListingAndMarketplace(listingId, Marketplace.WILLHABEN.name)
        val link = publication?.remoteActionsJson?.toStatusActionLinks()?.firstOrNull { it.action == action }
            ?: return Result.Error(StatusActionError.NotAvailable)
        val route = ContextLink(link.serviceName, link.relativePath).route
        val result = requestRunner.run {
            if (action == WillhabenStatusAction.DELETE) writeDataSource.delete(route) else writeDataSource.put(route)
        }
        if (result is Result.Error) return Result.Error(StatusActionError.Failed(result.error))
        // Status and the offered actions change with every action
        importer.importMyListings()
        return Result.Success(Unit)
    }
}
