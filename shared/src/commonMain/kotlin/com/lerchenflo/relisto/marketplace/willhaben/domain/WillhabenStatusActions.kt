package com.lerchenflo.relisto.marketplace.willhaben.domain

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.EmptyResult
import com.lerchenflo.relisto.app.domain.Error

/**
 * Status changes willhaben offers per listing in "Meine Anzeigen" (`advertEditStatusActionsList`),
 * with the link ids the web client uses. Paid actions (extras, bills) are left out.
 */
enum class WillhabenStatusAction(val linkId: String, val needsConfirmation: Boolean = false) {
    ACTIVATE("4"),
    DEACTIVATE("2"),
    RESERVE("markAdReserved"),
    UNRESERVE("removeMarkAdReserved"),
    SOLD("setAdSold", needsConfirmation = true),
    DELETE("3", needsConfirmation = true);

    companion object {
        fun ofLinkId(linkId: String): WillhabenStatusAction? = entries.firstOrNull { it.linkId == linkId }
    }
}

/** One action willhaben offers right now; the request goes to `/{serviceName}{relativePath}`. */
data class WillhabenStatusActionLink(
    val action: WillhabenStatusAction,
    val serviceName: String,
    val relativePath: String,
)

sealed interface StatusActionError : Error {
    /** willhaben does not offer this action for the listing (any more); the next import shows what it offers. */
    data object NotAvailable : StatusActionError

    data class Failed(val error: DataError) : StatusActionError
}

interface WillhabenStatusActions {
    /** Runs [action] on the listing's willhaben listing and imports again, so status and actions are current. */
    suspend fun perform(listingId: Long, action: WillhabenStatusAction): EmptyResult<StatusActionError>
}
