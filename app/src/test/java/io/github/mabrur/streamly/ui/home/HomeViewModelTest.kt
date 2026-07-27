package io.github.mabrur.streamly.ui.home

import app.cash.turbine.test
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.usecase.GetHomeFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun video(id: String, category: String) = Video(
    id = id,
    title = "Title $id",
    channelName = "Channel",
    channelAvatarUrl = "a",
    thumbnailUrl = "t",
    hlsUrl = "h",
    durationMs = 60_000L,
    viewCount = 100L,
    publishedAtEpochSeconds = 1_000L,
    category = category,
)

private class FakeCatalogRepository(
    var result: Result<HomeFeed>,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = result
    override suspend fun getShorts(): Result<List<Short>> = Result.success(emptyList())
    override suspend fun getVideo(id: String): Result<Video> = Result.failure(AppError.NotFound)
    override suspend fun getRelated(id: String): Result<List<Video>> = Result.success(emptyList())
    override suspend fun getProfile(): Result<UserProfile> = Result.failure(AppError.NotFound)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val feed = HomeFeed(
        categories = listOf(Category("All"), Category("Music"), Category("Gaming")),
        videos = listOf(video("a", "Music"), video("b", "Gaming")),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(result: Result<HomeFeed> = Result.success(feed)) =
        HomeViewModel(GetHomeFeedUseCase(FakeCatalogRepository(result)))

    @Test
    fun `emits loading then content on init`() = runTest {
        val vm = viewModel()

        vm.state.test {
            assertTrue(awaitItem().isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(listOf("a", "b"), loaded.videos.map { it.id })
            assertEquals(listOf("All", "Music", "Gaming"), loaded.categories)
            assertEquals(null, loaded.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits loading then error on failure`() = runTest {
        val vm = viewModel(Result.failure(AppError.Network))

        vm.state.test {
            assertTrue(awaitItem().isLoading)

            val failed = awaitItem()
            assertFalse(failed.isLoading)
            assertEquals(AppError.Network, failed.error)
            assertTrue(failed.videos.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CategorySelected filters the feed and updates the selection`() = runTest {
        val vm = viewModel()

        vm.state.test {
            skipItems(2)

            vm.onIntent(HomeIntent.CategorySelected("Gaming"))

            skipItems(1)
            val filtered = awaitItem()
            assertEquals("Gaming", filtered.selectedCategory)
            assertEquals(listOf("b"), filtered.videos.map { it.id })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Refresh clears a previous error`() = runTest {
        val repository = FakeCatalogRepository(Result.failure(AppError.Network))
        val vm = HomeViewModel(GetHomeFeedUseCase(repository))

        vm.state.test {
            skipItems(2)

            repository.result = Result.success(feed)
            vm.onIntent(HomeIntent.Refresh)

            skipItems(1)
            val recovered = awaitItem()
            assertEquals(null, recovered.error)
            assertEquals(2, recovered.videos.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `VideoClicked emits an OpenPlayer effect`() = runTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(HomeIntent.VideoClicked("a"))

            assertEquals(HomeEffect.OpenPlayer("a"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `VideoClicked does not change state`() = runTest {
        val vm = viewModel()

        vm.state.test {
            skipItems(2)

            vm.onIntent(HomeIntent.VideoClicked("a"))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
