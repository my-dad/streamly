package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject

class SignOutUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    suspend operator fun invoke() = repository.signOut()
}
