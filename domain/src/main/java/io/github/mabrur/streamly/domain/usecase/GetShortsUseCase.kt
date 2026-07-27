package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetShortsUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(): Result<List<Short>> = repository.getShorts()
}
