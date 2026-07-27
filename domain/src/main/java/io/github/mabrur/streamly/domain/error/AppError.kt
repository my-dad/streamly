package io.github.mabrur.streamly.domain.error

/**
 * Sealed error hierarchy for everything the UI must render.
 *
 * Extends [Exception] so repositories can return [Result], keeping the stdlib
 * combinators, while UiState still holds a sealed type rather than a raw String.
 */
sealed class AppError(message: String) : Exception(message) {
    data object Network : AppError("network unavailable")
    data object NotFound : AppError("resource not found")
    data object Storage : AppError("local storage failure")
    data class Unknown(val detail: String) : AppError(detail)
}
