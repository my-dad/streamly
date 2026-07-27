package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import javax.inject.Inject

class GetVideoDetailUseCase @Inject constructor(
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(videoId: String): Result<Video> = repository.getVideo(videoId)
}
