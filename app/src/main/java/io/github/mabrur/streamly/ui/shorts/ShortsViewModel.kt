package io.github.mabrur.streamly.ui.shorts

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.core.player.shorts.ShortsPool
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.usecase.GetShortsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val getShorts: GetShortsUseCase,
    private val pool: ShortsPool,
) : ViewModel() {

    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onIntent(intent: ShortsIntent) {
        when (intent) {
            ShortsIntent.Retry -> load()
            is ShortsIntent.PageSettled -> {
                if (_state.value.settledIndex == intent.index) return
                _state.update { it.copy(settledIndex = intent.index) }
                applyPool(intent.index)
            }
        }
    }

    fun playerForPage(pageIndex: Int): Player = pool.playerForPage(pageIndex)

    fun onStart() = pool.resumeSettled()

    /** Pausing everything here is what stops audio bleeding into Home. */
    fun onStop() = pool.pauseAll()

    private fun applyPool(settledIndex: Int) {
        pool.onSettled(settledIndex, _state.value.shorts.map { it.hlsUrl })
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            getShorts()
                .onSuccess { shorts ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            shorts = shorts.map { s -> s.toUi() },
                            error = null,
                        )
                    }
                    applyPool(_state.value.settledIndex)
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            shorts = emptyList(),
                            error = throwable as? AppError
                                ?: AppError.Unknown(throwable.message.orEmpty()),
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        pool.release()
        super.onCleared()
    }

    /** `onCleared` is protected; this is the only way a JVM test can assert the release. */
    @VisibleForTesting
    internal fun invokeOnCleared() = onCleared()
}
