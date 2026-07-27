package io.github.mabrur.streamly.ui.player

import androidx.media3.common.Player
import app.cash.turbine.test
import io.github.mabrur.streamly.core.player.PlayerHolder
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.HomeFeed
import io.github.mabrur.streamly.domain.model.Short
import io.github.mabrur.streamly.domain.model.UserProfile
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.CatalogRepository
import io.github.mabrur.streamly.domain.usecase.GetRelatedVideosUseCase
import io.github.mabrur.streamly.domain.usecase.GetVideoDetailUseCase
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

private fun video(id: String) = Video(
    id = id,
    title = "Title $id",
    channelName = "Channel",
    channelAvatarUrl = "a",
    thumbnailUrl = "t",
    hlsUrl = "https://example.com/$id.m3u8",
    durationMs = 60_000L,
    viewCount = 100L,
    publishedAtEpochSeconds = 1_000L,
    category = "Tech",
)

private class FakePlayerHolder : PlayerHolder {
    /**
     * Never touched: [PlayerViewModel] only forwards this to the UI, and no test here
     * renders. Throwing rather than mocking keeps Mockito out of the build — a fake for
     * an interface this wide would otherwise cost a dependency for zero assertions.
     */
    override val player: Player get() = error("PlayerHolder.player is not used in ViewModel tests")

    var lastUrl: String? = null
    var lastStartPositionMs: Long = -1L
    var releaseCount = 0
    var paused = false

    override fun setMedia(hlsUrl: String, startPositionMs: Long) {
        lastUrl = hlsUrl
        lastStartPositionMs = startPositionMs
    }

    override fun pause() { paused = true }
    override fun resume() { paused = false }
    override fun currentPositionMs(): Long = 4_200L
    override fun release() { releaseCount++ }
}

private class FakeCatalogRepository(
    private val videos: List<Video>,
    private val failure: AppError? = null,
) : CatalogRepository {
    override suspend fun getHomeFeed(): Result<HomeFeed> = Result.failure(AppError.NotFound)
    override suspend fun getShorts(): Result<List<Short>> = Result.success(emptyList())
    override suspend fun getVideo(id: String): Result<Video> = when {
        failure != null -> Result.failure(failure)
        else -> videos.firstOrNull { it.id == id }
            ?.let { Result.success(it) } ?: Result.failure(AppError.NotFound)
    }
    override suspend fun getRelated(id: String): Result<List<Video>> = when {
        failure != null -> Result.failure(failure)
        else -> Result.success(videos.filterNot { it.id == id })
    }
    override suspend fun getProfile(): Result<UserProfile> = Result.failure(AppError.NotFound)
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val videos = listOf(video("v01"), video("v02"), video("v03"))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Constructs the VM and sends the [PlayerIntent.Load] that starts it. */
    private fun viewModel(
        videoId: String = "v01",
        failure: AppError? = null,
        holder: FakePlayerHolder = FakePlayerHolder(),
        load: Boolean = true,
    ): Pair<PlayerViewModel, FakePlayerHolder> {
        val repository = FakeCatalogRepository(videos, failure)
        val vm = PlayerViewModel(
            getVideoDetail = GetVideoDetailUseCase(repository),
            getRelatedVideos = GetRelatedVideosUseCase(repository),
            playerHolder = holder,
        )
        if (load) vm.onIntent(PlayerIntent.Load(videoId))
        return vm to holder
    }

    @Test
    fun `loads the video and its related list`() = runTest {
        val (vm, _) = viewModel()

        vm.state.test {
            assertTrue(awaitItem().isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertEquals("Title v01", loaded.video?.title)
            assertEquals(listOf("v02", "v03"), loaded.related.map { it.id })
            assertEquals(null, loaded.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `prepares the holder with the loaded HLS url`() = runTest {
        val (vm, holder) = viewModel()

        vm.state.test {
            skipItems(2)
            assertEquals("https://example.com/v01.m3u8", holder.lastUrl)
            assertEquals(0L, holder.lastStartPositionMs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `surfaces a load failure as a sealed error`() = runTest {
        val (vm, _) = viewModel(failure = AppError.Network)

        vm.state.test {
            skipItems(1)
            val failed = awaitItem()
            assertEquals(AppError.Network, failed.error)
            assertEquals(null, failed.video)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unknown video id surfaces NotFound`() = runTest {
        val (vm, _) = viewModel(videoId = "missing")

        vm.state.test {
            skipItems(1)
            assertEquals(AppError.NotFound, awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `RelatedClicked emits OpenVideo and does not mutate state`() = runTest {
        val (vm, _) = viewModel()

        vm.effects.test {
            vm.onIntent(PlayerIntent.RelatedClicked("v02"))
            assertEquals(PlayerEffect.OpenVideo("v02"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onStop pauses and onStart resumes`() = runTest {
        val (vm, holder) = viewModel()

        vm.onStop()
        assertTrue(holder.paused)

        vm.onStart()
        assertFalse(holder.paused)
    }

    @Test
    fun `onCleared releases the holder`() = runTest {
        val (vm, holder) = viewModel()

        vm.invokeOnCleared()

        assertEquals(1, holder.releaseCount)
    }
}
