package io.github.mabrur.streamly.ui.downloads

import app.cash.turbine.test
import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.DownloadStatus
import io.github.mabrur.streamly.domain.model.Video
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeDownloadRepository : DownloadRepository {
    val emitted = MutableStateFlow<List<DownloadItem>>(emptyList())
    val removed = mutableListOf<String>()

    override val downloads: Flow<List<DownloadItem>> = emitted
    override suspend fun download(video: Video) = Unit
    override suspend fun remove(videoId: String) {
        removed += videoId
    }
}

private fun item(
    id: String,
    status: DownloadStatus = DownloadStatus.Completed,
    bytes: Long = 0,
) = DownloadItem(
    videoId = id,
    title = "Title $id",
    thumbnailUrl = "https://example.test/$id.jpg",
    status = status,
    bytesDownloaded = bytes,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading and clears once the repository emits`() = runTest(dispatcher) {
        val repository = FakeDownloadRepository()
        val viewModel = DownloadsViewModel(repository)

        assertTrue(viewModel.state.value.isLoading)

        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `maps items and sums storage across them`() = runTest(dispatcher) {
        val repository = FakeDownloadRepository()
        val viewModel = DownloadsViewModel(repository)
        runCurrent()

        repository.emitted.value = listOf(
            item("a", bytes = 512),
            item("b", DownloadStatus.InProgress(40f), bytes = 1536),
        )
        runCurrent()

        val state = viewModel.state.value
        assertEquals(listOf("a", "b"), state.items.map { it.videoId })
        assertEquals("Title a", state.items[0].title)
        assertEquals("512 B", state.items[0].sizeLabel)
        assertEquals(DownloadStatus.InProgress(40f), state.items[1].status)
        // 512 + 1536 = 2048 bytes, against the design's presentational 8 GB cap.
        assertEquals("2.0 KB used of 8.0 GB", state.storageLabel)
    }

    @Test
    fun `progress updates flow through without re-entering loading`() = runTest(dispatcher) {
        val repository = FakeDownloadRepository()
        val viewModel = DownloadsViewModel(repository)
        runCurrent()

        repository.emitted.value = listOf(item("a", DownloadStatus.InProgress(10f)))
        runCurrent()
        repository.emitted.value = listOf(item("a", DownloadStatus.InProgress(75f)))
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(DownloadStatus.InProgress(75f), viewModel.state.value.items.single().status)
    }

    @Test
    fun `RemoveClicked delegates to the repository`() = runTest(dispatcher) {
        val repository = FakeDownloadRepository()
        val viewModel = DownloadsViewModel(repository)
        runCurrent()

        viewModel.onIntent(DownloadsIntent.RemoveClicked("a"))
        runCurrent()

        assertEquals(listOf("a"), repository.removed)
    }

    @Test
    fun `PlayClicked on a completed download emits OpenPlayer`() = runTest(dispatcher) {
        val repository = FakeDownloadRepository()
        val viewModel = DownloadsViewModel(repository)
        runCurrent()
        repository.emitted.value = listOf(item("a", DownloadStatus.Completed))
        runCurrent()

        viewModel.effects.test {
            viewModel.onIntent(DownloadsIntent.PlayClicked("a"))
            assertEquals(DownloadsEffect.OpenPlayer("a"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `PlayClicked on an unfinished download toasts instead of opening`() =
        runTest(dispatcher) {
            val repository = FakeDownloadRepository()
            val viewModel = DownloadsViewModel(repository)
            runCurrent()
            repository.emitted.value = listOf(item("a", DownloadStatus.InProgress(30f)))
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(DownloadsIntent.PlayClicked("a"))
                assertEquals(DownloadsEffect.ShowToast("Still downloading…"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
