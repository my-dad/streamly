package io.github.mabrur.streamly.ui.shorts

import androidx.media3.common.Player
import app.cash.turbine.test
import io.github.mabrur.streamly.core.player.shorts.ShortsPool
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.usecase.GetShortsUseCase
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

private fun short(id: String) = Short(
    id = id,
    title = "Short $id",
    channelName = "Channel",
    hlsUrl = "https://example.com/$id.m3u8",
    likeCount = 1_500L,
    commentCount = 12L,
)

private class FakeCatalogRepository(
    private val result: Result<List<Short>>,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = Result.failure(AppError.NotFound)
    override suspend fun getShorts(): Result<List<Short>> = result
    override suspend fun getVideo(id: String): Result<Video> = Result.failure(AppError.NotFound)
    override suspend fun getRelated(id: String): Result<List<Video>> = Result.success(emptyList())
    override suspend fun getProfile(): Result<UserProfile> = Result.failure(AppError.NotFound)
}

private class FakeShortsPool : ShortsPool {
    val settledCalls = mutableListOf<Pair<Int, List<String>>>()
    var releaseCount = 0
    var paused = false

    // The ViewModel only forwards this to the pager; no ViewModel test needs a real Player,
    // and constructing one would drag the Android framework into a JVM test.
    override fun playerForPage(pageIndex: Int): Player =
        error("playerForPage is not used in ViewModel tests")

    override fun onSettled(settledIndex: Int, urls: List<String>) {
        settledCalls += settledIndex to urls
    }

    override fun pauseAll() {
        paused = true
    }

    override fun resumeSettled() {
        paused = false
    }

    override fun release() {
        releaseCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val shorts = listOf(short("s01"), short("s02"), short("s03"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        result: Result<List<Short>> = Result.success(shorts),
        pool: FakeShortsPool = FakeShortsPool(),
    ): Pair<ShortsViewModel, FakeShortsPool> =
        ShortsViewModel(GetShortsUseCase(FakeCatalogRepository(result)), pool) to pool

    @Test
    fun `loads shorts and maps them for display`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            assertTrue(awaitItem().isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals(listOf("s01", "s02", "s03"), loaded.shorts.map { it.id })
            assertEquals("1.5K", loaded.shorts.first().likeLabel)
            assertEquals("12", loaded.shorts.first().commentLabel)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces a failure as a sealed error`() = runTest {
        val (vm, _) = viewModel(Result.failure(AppError.Network))

        vm.state.test {
            skipItems(1)
            val failed = awaitItem()
            assertEquals(AppError.Network, failed.error)
            assertTrue(failed.shorts.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PageSettled records the settled index`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            skipItems(2)

            vm.onIntent(ShortsIntent.PageSettled(2))

            assertEquals(2, awaitItem().settledIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeat PageSettled for the same index does not re-emit`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            skipItems(2)

            vm.onIntent(ShortsIntent.PageSettled(1))
            assertEquals(1, awaitItem().settledIndex)

            vm.onIntent(ShortsIntent.PageSettled(1))
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the initial settled index is zero`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            skipItems(1)
            assertEquals(0, awaitItem().settledIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `settling a page drives the pool and clearing releases it`() = runTest {
        val (vm, pool) = viewModel()

        vm.state.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.onIntent(ShortsIntent.PageSettled(1))

        assertEquals(1, pool.settledCalls.last().first)
        assertEquals(3, pool.settledCalls.last().second.size)

        vm.onStop()
        assertTrue(pool.paused)

        vm.onStart()
        assertFalse(pool.paused)

        vm.invokeOnCleared()
        assertEquals(1, pool.releaseCount)
    }

    @Test
    fun `loading the feed primes the pool with the initial page`() = runTest {
        val (vm, pool) = viewModel()

        vm.state.test { skipItems(2); cancelAndIgnoreRemainingEvents() }

        assertEquals(1, pool.settledCalls.size)
        assertEquals(0, pool.settledCalls.single().first)
        assertEquals(
            shorts.map { it.hlsUrl },
            pool.settledCalls.single().second,
        )
    }

    @Test
    fun `a failed load never primes the pool`() = runTest {
        val (vm, pool) = viewModel(Result.failure(AppError.Network))

        vm.state.test { skipItems(2); cancelAndIgnoreRemainingEvents() }

        assertTrue(pool.settledCalls.isEmpty())
    }
}
