package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetHomeFeedUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(category: Category = Category.All): Result<HomeFeed> =
        repository.getHomeFeed().map { it.filteredBy(category) }
}
