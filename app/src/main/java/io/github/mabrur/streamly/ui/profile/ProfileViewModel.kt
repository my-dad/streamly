package io.github.mabrur.streamly.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.usecase.SignOutUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> load()
            ProfileIntent.SignOutClicked ->
                _state.update { it.copy(showSignOutDialog = true) }
            ProfileIntent.SignOutDismissed ->
                _state.update { it.copy(showSignOutDialog = false) }
            ProfileIntent.SignOutConfirmed -> {
                _state.update { it.copy(showSignOutDialog = false) }
                viewModelScope.launch {
                    // Clearing the session flips SessionState to SignedOut, which makes
                    // StreamlyApp rebuild the nav host rooted at Onboarding. No effect
                    // needed — and the stack is cleared, not popped.
                    signOut()
                }
            }
        }
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            catalogRepository.getProfile()
                .onSuccess { profile ->
                    _state.update { it.copy(isLoading = false, profile = profile, error = null) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = throwable as? AppError
                                ?: AppError.Unknown(throwable.message.orEmpty()),
                        )
                    }
                }
        }
    }
}
