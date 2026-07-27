package io.github.mabrur.streamly.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mabrur.streamly.domain.error.AppError
import io.github.mabrur.streamly.domain.model.Category
import io.github.mabrur.streamly.domain.usecase.GetHomeFeedUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: GetHomeFeedUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(HomeUiState.ALL_CATEGORY)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> load(_state.value.selectedCategory)
            is HomeIntent.CategorySelected -> load(intent.category)
            is HomeIntent.VideoClicked -> viewModelScope.launch {
                _effects.send(HomeEffect.OpenPlayer(intent.videoId))
            }
            HomeIntent.ProfileClicked -> viewModelScope.launch {
                _effects.send(HomeEffect.OpenProfile)
            }
        }
    }

    private fun load(category: String) {
        loadJob?.cancel()
        _state.update {
            it.copy(isLoading = true, error = null, selectedCategory = category)
        }
        loadJob = viewModelScope.launch {
            val nowSeconds = System.currentTimeMillis() / 1_000
            getHomeFeed(Category(category))
                .onSuccess { feed ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            videos = feed.videos.map { video -> video.toUi(nowSeconds) },
                            categories = feed.categories.map(Category::name),
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            videos = emptyList(),
                            error = throwable as? AppError
                                ?: AppError.Unknown(throwable.message.orEmpty()),
                        )
                    }
                }
        }
    }
}
