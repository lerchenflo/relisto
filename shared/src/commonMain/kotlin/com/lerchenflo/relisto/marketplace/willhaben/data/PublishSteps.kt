package com.lerchenflo.relisto.marketplace.willhaben.data

import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishError
import com.lerchenflo.relisto.marketplace.willhaben.domain.PublishStep
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Runs one request of a publish flow; a failure names the [step] it happened in. */
internal suspend fun <T> WillhabenRequestRunner.step(
    step: PublishStep,
    request: suspend () -> Result<T, DataError.Network>,
): Result<T, PublishError> = when (val result = run(request)) {
    is Result.Error -> Result.Error(PublishError.Failed(step, result.error))
    is Result.Success -> result
}

/** Takes the ids and links willhaben assigned to a presaved draft. */
internal fun JsonObject.mergeIds(presaved: JsonObject): JsonObject =
    JsonObject(this + presaved.filterKeys { it in PRESAVE_KEYS && presaved[it] !is JsonNull })

private val PRESAVE_KEYS = setOf("adId", "adUuid", "productId", "contextLinkList")

/** The value, or [onError] (which returns from the calling flow) for a failure. */
internal inline fun <T> Result<T, PublishError>.valueOr(onError: (Result.Error<PublishError>) -> Nothing): T =
    when (this) {
        is Result.Error -> onError(this)
        is Result.Success -> data
    }
