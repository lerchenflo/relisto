package com.lerchenflo.relisto.marketplace.willhaben.presentation

import com.lerchenflo.relisto.marketplace.willhaben.domain.MissingField
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.StatusActionError
import com.lerchenflo.relisto.utilities.PriceFormatter
import com.lerchenflo.relisto.utilities.UiText
import com.lerchenflo.relisto.utilities.toUiText
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.missing_category
import relisto.shared.generated.resources.missing_location
import relisto.shared.generated.resources.missing_title
import relisto.shared.generated.resources.publish_error_already
import relisto.shared.generated.resources.publish_error_incomplete
import relisto.shared.generated.resources.push_error_conflict
import relisto.shared.generated.resources.republish_error_api_changed
import relisto.shared.generated.resources.republish_error_not_published
import relisto.shared.generated.resources.republish_error_paid
import relisto.shared.generated.resources.republish_error_paid_package
import relisto.shared.generated.resources.status_action_not_available

fun StatusActionError.toUiText(): UiText = when (this) {
    StatusActionError.NotAvailable -> UiText.StringResourceText(Res.string.status_action_not_available)
    is StatusActionError.Failed -> error.toUiText()
}

fun MissingField.toUiText(): UiText = UiText.StringResourceText(
    when (this) {
        MissingField.TITLE -> Res.string.missing_title
        MissingField.CATEGORY -> Res.string.missing_category
        MissingField.LOCATION -> Res.string.missing_location
    }
)

fun PublishError.toUiText(): UiText = when (this) {
    PublishError.NotPublished -> UiText.StringResourceText(Res.string.republish_error_not_published)
    is PublishError.PaidProduct -> cheapestPackage?.let {
        UiText.StringResourceText(Res.string.republish_error_paid_package, arrayOf(it.name, PriceFormatter.format(it.priceCents)))
    } ?: UiText.StringResourceText(Res.string.republish_error_paid)
    PublishError.Conflict -> UiText.StringResourceText(Res.string.push_error_conflict)
    PublishError.AlreadyPublished -> UiText.StringResourceText(Res.string.publish_error_already)
    is PublishError.Incomplete -> UiText.StringResourceText(
        Res.string.publish_error_incomplete,
        arrayOf(UiText.Joined(missing.map { it.toUiText() })),
    )
    is PublishError.MissingLink -> UiText.StringResourceText(Res.string.republish_error_api_changed, arrayOf(linkId))
    is PublishError.Failed -> error.toUiText()
}
