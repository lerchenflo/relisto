package com.lerchenflo.willhabenupdater.utilities

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Text for the UI that is either a string resource or a dynamic value.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    class StringResourceText(
        val resId: StringResource,
        val args: Array<Any> = emptyArray()
    ) : UiText {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringResourceText) return false
            return resId == other.resId && args.contentEquals(other.args)
        }

        override fun hashCode(): Int = 31 * resId.hashCode() + args.contentHashCode()
    }

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResourceText -> stringResource(resId, *args)
        }
    }

    /** Resolves the text outside of composition, e.g. for a snackbar. */
    suspend fun resolve(): String {
        return when (this) {
            is DynamicString -> value
            is StringResourceText -> getString(resId, *args)
        }
    }
}
