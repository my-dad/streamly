package io.github.mabrur.streamly.domain.repository

import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video

interface CatalogRepository {
    suspend fun getHomeFeed(): Result<HomeFeed>
    suspend fun getShorts(): Result<List<Short>>
    suspend fun getVideo(id: String): Result<Video>
    suspend fun getRelated(id: String): Result<List<Video>>
    suspend fun getProfile(): Result<UserProfile>
}
