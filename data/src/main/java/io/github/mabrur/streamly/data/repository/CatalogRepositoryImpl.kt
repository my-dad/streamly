package io.github.mabrur.streamly.data.repository

import io.github.mabrur.streamly.data.remote.CatalogApi
import io.github.mabrur.streamly.data.remote.mapper.toHomeFeed
import io.github.mabrur.streamly.data.remote.mapper.toProfile
import io.github.mabrur.streamly.data.remote.mapper.toShorts
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.ktor.client.plugins.ResponseException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal class CatalogRepositoryImpl @Inject constructor(
    private val api: CatalogApi,
) : CatalogRepository {

    override suspend fun getHomeFeed(): Result<HomeFeed> =
        catching { api.fetchCatalog().toHomeFeed() }

    override suspend fun getShorts(): Result<List<Short>> =
        catching { api.fetchCatalog().toShorts() }

    override suspend fun getVideo(id: String): Result<Video> =
        catching { api.fetchCatalog().toHomeFeed().videos }
            .mapCatching { videos ->
                videos.firstOrNull { it.id == id } ?: throw AppError.NotFound
            }

    override suspend fun getRelated(id: String): Result<List<Video>> =
        catching { api.fetchCatalog().toHomeFeed().videos.filterNot { it.id == id } }

    override suspend fun getProfile(): Result<UserProfile> =
        catching { api.fetchCatalog().toProfile() }
}

/**
 * Like [runCatching], but rethrows [CancellationException] so structured
 * concurrency is not silently broken. Do not replace this with runCatching.
 */
private inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: AppError) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(AppError.Network)
} catch (e: ResponseException) {
    Result.failure(AppError.Network)
} catch (e: Throwable) {
    Result.failure(AppError.Unknown(e.message ?: e::class.simpleName.orEmpty()))
}
