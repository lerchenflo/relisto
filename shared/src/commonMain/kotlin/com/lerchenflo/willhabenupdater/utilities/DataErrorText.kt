package com.lerchenflo.willhabenupdater.utilities

import com.lerchenflo.willhabenupdater.app.domain.DataError
import willhabenupdater.shared.generated.resources.Res
import willhabenupdater.shared.generated.resources.error_disk_full
import willhabenupdater.shared.generated.resources.error_no_internet
import willhabenupdater.shared.generated.resources.error_not_found
import willhabenupdater.shared.generated.resources.error_request_timeout
import willhabenupdater.shared.generated.resources.error_serialization
import willhabenupdater.shared.generated.resources.error_server
import willhabenupdater.shared.generated.resources.error_too_many_requests
import willhabenupdater.shared.generated.resources.error_unauthorized
import willhabenupdater.shared.generated.resources.error_unknown

fun DataError.toUiText(): UiText {
    val resource = when (this) {
        DataError.Network.NO_INTERNET -> Res.string.error_no_internet
        DataError.Network.REQUEST_TIMEOUT -> Res.string.error_request_timeout
        DataError.Network.UNAUTHORIZED,
        DataError.Network.FORBIDDEN -> Res.string.error_unauthorized
        DataError.Network.NOT_FOUND,
        DataError.Local.NOT_FOUND -> Res.string.error_not_found
        DataError.Network.TOO_MANY_REQUESTS -> Res.string.error_too_many_requests
        DataError.Network.SERVER_ERROR,
        DataError.Network.SERVICE_UNAVAILABLE -> Res.string.error_server
        DataError.Network.SERIALIZATION -> Res.string.error_serialization
        DataError.Local.DISK_FULL -> Res.string.error_disk_full
        DataError.Network.BAD_REQUEST,
        DataError.Network.CONFLICT,
        DataError.Network.PAYLOAD_TOO_LARGE,
        DataError.Network.UNKNOWN,
        DataError.Local.UNKNOWN -> Res.string.error_unknown
    }
    return UiText.StringResourceText(resource)
}
