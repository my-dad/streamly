package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.SessionState
import io.github.mabrur.streamly.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(
    private val repository: SessionRepository,
) {
    operator fun invoke(): Flow<SessionState> = repository.state
}
