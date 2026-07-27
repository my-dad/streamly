package io.github.mabrur.streamly.ui.onboarding

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingUiState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: OnboardingError? = null,
)

/** Screen-local validation, distinct from the domain's AppError. */
sealed interface OnboardingError {
    data object InvalidEmail : OnboardingError
}

sealed interface OnboardingIntent {
    data object ContinueWithGoogle : OnboardingIntent
    data object ContinueAsGuest : OnboardingIntent
    data class EmailChanged(val value: String) : OnboardingIntent
    data object SubmitEmail : OnboardingIntent
}
