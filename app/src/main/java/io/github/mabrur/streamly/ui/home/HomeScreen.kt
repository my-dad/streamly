package io.github.mabrur.streamly.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mabrur.streamly.core.designsystem.component.CategoryChipRow
import io.github.mabrur.streamly.core.designsystem.component.ContentState
import io.github.mabrur.streamly.core.designsystem.component.VideoCard

@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (state.categories.isNotEmpty()) {
            CategoryChipRow(
                categories = state.categories,
                selected = state.selectedCategory,
                onSelect = { onIntent(HomeIntent.CategorySelected(it)) },
            )
        }

        ContentState(
            isLoading = state.isLoading,
            error = state.error,
            data = state.videos,
            isEmpty = { it.isEmpty() },
            onRetry = { onIntent(HomeIntent.Refresh) },
        ) { videos ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                items(items = videos, key = { it.id }) { video ->
                    VideoCard(
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        metaLine = video.metaLine,
                        durationLabel = video.durationLabel,
                        onClick = { onIntent(HomeIntent.VideoClicked(video.id)) },
                    )
                }
            }
        }
    }
}
