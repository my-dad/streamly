package io.github.mabrur.streamly.domain.usecase

import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun video(id: String, category: String) = Video(
    id = id,
    title = "Title $id",
    channelName = "Channel",
    channelAvatarUrl = "https://example.com/a.png",
    thumbnailUrl = "https://example.com/t.png",
    hlsUrl = "https://example.com/$id.m3u8",
    durationMs = 60_000L,
    viewCount = 100L,
    publishedAtEpochSeconds = 1_700_000_000L,
    category = category,
)

private class FakeCatalogRepository(
    private val result: Result<HomeFeed>,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = result
    override suspend fun getShorts(): Result<List<Short>> = Result.success(emptyList())
    override suspend fun getVideo(id: String): Result<Video> = Result.failure(AppError.NotFound)
    override suspend fun getRelated(id: String): Result<List<Video>> = Result.success(emptyList())
    override suspend fun getProfile(): Result<UserProfile> = Result.failure(AppError.NotFound)
}

class GetHomeFeedUseCaseTest {

    private val feed = HomeFeed(
        categories = listOf(Category("Music"), Category("Gaming")),
        videos = listOf(video("a", "Music"), video("b", "Gaming")),
    )

    @Test
    fun `returns the full feed for Category All`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase(Category.All)

        assertEquals(2, result.getOrThrow().videos.size)
    }

    @Test
    fun `filters the feed by the requested category`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase(Category("Gaming"))

        assertEquals(listOf("b"), result.getOrThrow().videos.map { it.id })
    }

    @Test
    fun `defaults to Category All when no category is given`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.success(feed)))

        val result = useCase()

        assertEquals(2, result.getOrThrow().videos.size)
    }

    @Test
    fun `propagates repository failure unchanged`() = runTest {
        val useCase = GetHomeFeedUseCase(FakeCatalogRepository(Result.failure(AppError.Network)))

        val result = useCase(Category.All)

        assertTrue(result.isFailure)
        assertEquals(AppError.Network, result.exceptionOrNull())
    }
}
