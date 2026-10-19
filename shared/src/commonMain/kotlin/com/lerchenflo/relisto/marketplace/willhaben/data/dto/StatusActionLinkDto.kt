package com.lerchenflo.relisto.marketplace.willhaben.data.dto

import kotlinx.serialization.Serializable

/** One entry of `listing_publications.remoteActionsJson`; [action] is a WillhabenStatusAction name. */
@Serializable
data class StatusActionLinkDto(
    val action: String,
    val serviceName: String,
    val relativePath: String,
)
