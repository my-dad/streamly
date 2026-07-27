package io.github.mabrur.streamly.domain.model

data class Session(
    val userId: String,
    val displayName: String,
    val email: String,
    val isGuest: Boolean,
)

sealed interface SessionState {
    /** Persisted state has not been read yet. Hold the splash while this is current. */
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val session: Session) : SessionState
}
