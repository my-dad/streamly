package io.github.mabrur.streamly.ui.player

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.core.player.PlayerHolder
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.usecase.GetRelatedVideosUseCase
import io.github.mabrur.streamly.domain.usecase.GetVideoDetailUseCase
import io.github.mabrur.streamly.ui.home.toUi
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
class PlayerViewModel @Inject constructor(
    private val getVideoDetail: GetVideoDetailUseCase,
    private val getRelatedVideos: GetRelatedVideosUseCase,
    private val playerHolder: PlayerHolder,
) : ViewModel() {

    private var videoId: String? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _effects = Channel<PlayerEffect>(Channel.BUFFERED)
    val effects: Flow<PlayerEffect> = _effects.receiveAsFlow()

    /** Exposed for [androidx.media3.ui.compose.PlayerSurface] and the media3 state holders. */
    val player: Player get() = playerHolder.player

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            is PlayerIntent.Load -> {
                // Guard against re-loading on every recomposition or config change.
                if (videoId == intent.videoId) return
                videoId = intent.videoId
                load()
            }
            PlayerIntent.Retry -> load()
            is PlayerIntent.RelatedClicked -> viewModelScope.launch {
                _effects.send(PlayerEffect.OpenVideo(intent.videoId))
            }
            // Wired to the real DownloadRepository in the Downloads plan.
            PlayerIntent.DownloadClicked -> viewModelScope.launch {
                _effects.send(PlayerEffect.DownloadStarted)
            }
        }
    }

    /** Called from LifecycleStartEffect. Pause on background. */
    fun onStop() = playerHolder.pause()

    /** Called from LifecycleStartEffect. Resume on return. */
    fun onStart() = playerHolder.resume()

    private fun load() {
        val id = videoId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val nowSeconds = System.currentTimeMillis() / 1_000

            getVideoDetail(id)
                .onSuccess { video ->
                    playerHolder.setMedia(video.hlsUrl, startPositionMs = 0L)
                    val related = getRelatedVideos(id).getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            isLoading = false,
                            video = video.toUi(nowSeconds),
                            hlsUrl = video.hlsUrl,
                            related = related.map { r -> r.toUi(nowSeconds) },
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            video = null,
                            error = throwable as? AppError
                                ?: AppError.Unknown(throwable.message.orEmpty()),
                        )
                    }
                }
        }
    }

    /**
     * The single release point. Nav3's ViewModel decorator (D-007) disposes this entry
     * when the Player key is popped, which lands here.
     */
    override fun onCleared() {
        playerHolder.release()
        super.onCleared()
    }

    @VisibleForTesting
    internal fun invokeOnCleared() = onCleared()
}
