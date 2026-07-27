package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Session
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject

class SignInUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    suspend operator fun invoke(session: Session) = repository.signIn(session)
}
