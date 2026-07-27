package io.github.mabrur.streamly.core.designsystem.error

import androidx.annotation.StringRes
import io.github.mabrur.streamly.core.designsystem.R
import io.github.mabrur.streamly.domain.error.AppError

@StringRes
fun AppError.titleResId(): Int = when (this) {
    AppError.Network -> R.string.error_network_title
    AppError.NotFound -> R.string.error_not_found_title
    AppError.Storage -> R.string.error_storage_title
    is AppError.Unknown -> R.string.error_generic_title
}

@StringRes
fun AppError.bodyResId(): Int = when (this) {
    AppError.Network -> R.string.error_network_body
    AppError.NotFound -> R.string.error_not_found_body
    AppError.Storage -> R.string.error_storage_body
    is AppError.Unknown -> R.string.error_generic_body
}

/** Only transient failures offer a retry affordance. */
fun AppError.isRetryable(): Boolean = when (this) {
    AppError.Network -> true
    is AppError.Unknown -> true
    AppError.NotFound -> false
    AppError.Storage -> false
}
