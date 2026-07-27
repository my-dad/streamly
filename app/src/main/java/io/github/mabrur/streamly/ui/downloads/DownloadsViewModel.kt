package io.github.mabrur.streamly.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.core.designsystem.format.formatBytes
import io.github.mabrur.streamly.core.designsystem.format.formatStorageLine
import io.github.mabrur.streamly.domain.model.DownloadItem
import io.github.mabrur.streamly.domain.model.DownloadStatus
import io.github.mabrur.streamly.domain.repository.DownloadRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsUiState(isLoading = true))
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    private val _effects = Channel<DownloadsEffect>(Channel.BUFFERED)
    val effects: Flow<DownloadsEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            downloadRepository.downloads.collect { items ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = items.map(DownloadItem::toRowUi),
                        storageLabel = formatStorageLine(
                            usedBytes = items.sumOf { item -> item.bytesDownloaded },
                            capBytes = STORAGE_CAP_BYTES,
                        ),
                        error = null,
                    )
                }
            }
        }
    }

    fun onIntent(intent: DownloadsIntent) {
        when (intent) {
            is DownloadsIntent.RemoveClicked -> viewModelScope.launch {
                downloadRepository.remove(intent.videoId)
            }
            is DownloadsIntent.PlayClicked -> viewModelScope.launch {
                // The row stays tappable while downloading so the toast can explain why
                // nothing opened. A disabled row just reads as broken.
                val item = _state.value.items.firstOrNull { it.videoId == intent.videoId }
                if (item?.status == DownloadStatus.Completed) {
                    _effects.send(DownloadsEffect.OpenPlayer(intent.videoId))
                } else {
                    _effects.send(DownloadsEffect.ShowToast("Still downloading…"))
                }
            }
        }
    }
}

/** Presentational only — matches the design. Nothing enforces a quota. */
private const val STORAGE_CAP_BYTES = 8L * 1024 * 1024 * 1024

private fun DownloadItem.toRowUi() = DownloadRowUi(
    videoId = videoId,
    title = title,
    thumbnailUrl = thumbnailUrl,
    status = status,
    sizeLabel = formatBytes(bytesDownloaded),
)
