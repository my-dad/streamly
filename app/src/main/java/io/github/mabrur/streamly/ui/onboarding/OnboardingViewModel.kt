package io.github.mabrur.streamly.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.usecase.SignInUseCase
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val signIn: SignInUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.EmailChanged ->
                _state.update { it.copy(email = intent.value, error = null) }

            // Auth is mocked, as the PRD explicitly permits. The session pipeline is real.
            OnboardingIntent.ContinueWithGoogle -> persist(
                Session(
                    userId = UUID.randomUUID().toString(),
                    displayName = "Mabrur Chowdhury",
                    email = "mabrur@example.com",
                    isGuest = false,
                )
            )

            OnboardingIntent.ContinueAsGuest -> persist(
                Session(
                    userId = UUID.randomUUID().toString(),
                    displayName = "Guest",
                    email = "",
                    isGuest = true,
                )
            )

            OnboardingIntent.SubmitEmail -> {
                val email = _state.value.email.trim()
                if (!isValidEmail(email)) {
                    _state.update { it.copy(error = OnboardingError.InvalidEmail) }
                } else {
                    persist(
                        Session(
                            userId = UUID.randomUUID().toString(),
                            displayName = email.substringBefore('@'),
                            email = email,
                            isGuest = false,
                        )
                    )
                }
            }
        }
    }

    /**
     * No navigation effect: writing the session flips SessionState to SignedIn, and
     * StreamlyApp rebuilds the nav host rooted at Home. Navigating here too would race it.
     */
    private fun persist(session: Session) {
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            signIn(session)
            _state.update { it.copy(isSubmitting = false) }
        }
    }

    /** Deliberately minimal — android.util.Patterns is unavailable in JVM unit tests. */
    private fun isValidEmail(value: String): Boolean =
        value.length in 3..254 &&
            value.count { it == '@' } == 1 &&
            value.substringBefore('@').isNotEmpty() &&
            value.substringAfter('@').contains('.') &&
            !value.endsWith('.')
}
