package io.github.mabrur.streamly.ui.navigation

import io.github.mabrur.streamly.domain.model.SessionState

/**
 * The start destination for a given session state.
 *
 * Returns `null` while the persisted session is still being read — the caller holds
 * a loading surface rather than guessing, which would flash Onboarding at a signed-in
 * user on every cold start.
 */
fun startKeyFor(state: SessionState): StreamlyKey? = when (state) {
    SessionState.Unknown -> null
    SessionState.SignedOut -> StreamlyKey.Onboarding
    is SessionState.SignedIn -> StreamlyKey.Home
}
