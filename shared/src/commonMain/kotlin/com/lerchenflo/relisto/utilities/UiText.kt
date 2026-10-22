package com.lerchenflo.relisto.utilities

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Text for the UI that is either a string resource or a dynamic value.
 * Arguments of a string resource may be [UiText] themselves; they are resolved first.
 */
sealed interface UiText {
    data class DynamicString(val value: String) : UiText

    /** Several texts in a row, e.g. a list of missing fields. */
    data class Joined(val parts: List<UiText>, val separator: String = ", ") : UiText

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
            is Joined -> parts.map { it.asString() }.joinToString(separator)
            is StringResourceText -> stringResource(resId, *args.map { (it as? UiText)?.asString() ?: it }.toTypedArray())
        }
    }

    /** Resolves the text outside of composition, e.g. for a snackbar. */
    suspend fun resolve(): String {
        return when (this) {
            is DynamicString -> value
            is Joined -> parts.map { it.resolve() }.joinToString(separator)
            is StringResourceText -> getString(resId, *args.map { (it as? UiText)?.resolve() ?: it }.toTypedArray())
        }
    }
}
