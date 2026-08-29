package com.mochame.utils.cli

sealed interface Update<out T> {
    data object Unchanged : Update<Nothing>
    data object Clear : Update<Nothing>
    data class Set<T>(val value: T) : Update<T>

    companion object {
        fun <T> fromParsed(rawInput: String?, parsedValue: T?): Update<T> = when {
            rawInput == null -> Unchanged
            rawInput.isBlank() -> Clear
            parsedValue != null -> Set(parsedValue)
            else -> Unchanged
        }

        fun <T : Any> fromNullable(value: T?): Update<T> = when (value) {
            null -> Unchanged
            else -> Set(value)
        }
    }
}

/**
 * Resolves an Update instruction against an existing stored value.
 */
fun <T> Update<T>.resolve(current: T?): T? = when (this) {
    is Update.Unchanged -> current
    is Update.Clear -> null
    is Update.Set -> value
}