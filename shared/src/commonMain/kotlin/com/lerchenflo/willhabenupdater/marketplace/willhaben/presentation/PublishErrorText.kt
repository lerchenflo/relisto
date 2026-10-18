package com.lerchenflo.willhabenupdater.marketplace.willhaben.presentation

import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.PublishError
import com.lerchenflo.willhabenupdater.utilities.UiText
import com.lerchenflo.willhabenupdater.utilities.toUiText
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.republish_error_api_changed
import willhabenupdater.shared.generated.resources.republish_error_not_published
import willhabenupdater.shared.generated.resources.republish_error_paid

fun PublishError.toUiText(): UiText = when (this) {
    PublishError.NotPublished -> UiText.StringResourceText(Res.string.republish_error_not_published)
    PublishError.PaidProduct -> UiText.StringResourceText(Res.string.republish_error_paid)
    is PublishError.MissingLink -> UiText.StringResourceText(Res.string.republish_error_api_changed, arrayOf(linkId))
    is PublishError.Failed -> error.toUiText()
}
