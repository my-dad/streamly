package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    val state: Flow<SessionState>
    suspend fun signIn(session: Session)
    suspend fun signOut()
}
