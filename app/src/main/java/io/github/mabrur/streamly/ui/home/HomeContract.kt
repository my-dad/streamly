package io.github.mabrur.streamly.ui.home

import androidx.compose.runtime.Immutable
import io.github.mabrur.streamly.domain.error.AppError

@Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoUi> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = ALL_CATEGORY,
    val error: AppError? = null,
) {
    companion object {
        const val ALL_CATEGORY = "All"
    }
}

sealed interface HomeIntent {
    data object Refresh : HomeIntent
    data class VideoClicked(val videoId: String) : HomeIntent
    data class CategorySelected(val category: String) : HomeIntent
}

sealed interface HomeEffect {
    data class OpenPlayer(val videoId: String) : HomeEffect
}
