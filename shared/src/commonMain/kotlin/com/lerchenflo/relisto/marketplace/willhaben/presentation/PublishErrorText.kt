package com.lerchenflo.relisto.marketplace.willhaben.presentation

import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.utilities.UiText
import com.lerchenflo.relisto.utilities.toUiText
import relisto.shared.generated.resources.Res
import relisto.shared.generated.resources.republish_error_api_changed
import relisto.shared.generated.resources.republish_error_not_published
import relisto.shared.generated.resources.republish_error_paid

fun PublishError.toUiText(): UiText = when (this) {
    PublishError.NotPublished -> UiText.StringResourceText(Res.string.republish_error_not_published)
    PublishError.PaidProduct -> UiText.StringResourceText(Res.string.republish_error_paid)
    is PublishError.MissingLink -> UiText.StringResourceText(Res.string.republish_error_api_changed, arrayOf(linkId))
    is PublishError.Failed -> error.toUiText()
}
