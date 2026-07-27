package io.github.mabrur.streamly.ui.profile

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.UserProfile

@Immutable
data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: UserProfile? = null,
    val showSignOutDialog: Boolean = false,
    val error: AppError? = null,
)

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object SignOutClicked : ProfileIntent
    data object SignOutConfirmed : ProfileIntent
    data object SignOutDismissed : ProfileIntent
}
